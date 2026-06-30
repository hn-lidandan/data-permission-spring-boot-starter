# Data Permission SDK 开发小白版步骤

## 1. 先理解一句话

你要做的 SDK，不是一个独立运行的系统。

它更像一个“插件包”：

```text
业务系统引入你的 SDK
        |
        v
SDK 自动帮业务系统做权限过滤、字段脱敏、导出控制、审计
```

所以开发 SDK 的重点不是写接口页面，而是写“别人引入后自动生效的能力”。

## 2. 一期只先做最小闭环

不要一开始就做完整权限平台。

一期先跑通这一条链路：

```text
请求进来
  -> SDK 拿到当前用户是谁
  -> SDK 获取这个用户的数据权限
  -> SDK 把权限保存到当前请求里
  -> 业务代码执行查询
  -> SDK 拦截 SQL
  -> SDK 给 SQL 自动加权限条件
  -> 数据库只返回有权限的数据
```

举例：

业务原始 SQL：

```sql
SELECT * FROM normalized_security_log WHERE event_type = ?
```

SDK 改写后：

```sql
SELECT * FROM normalized_security_log
WHERE event_type = ?
  AND tenant_id = ?
  AND asset_group_id IN (?, ?)
  AND data_level IN (?, ?)
```

这就是一期最重要的成功标志。

## 3. 开发顺序

## 第一步：把项目改成 Starter

### 你要做什么

让业务系统只要引入依赖，就能自动加载你的 SDK。

类似这样：

```xml
<dependency>
    <groupId>org.com.it</groupId>
    <artifactId>data-permission-spring-boot-starter</artifactId>
    <version>0.0.1-SNAPSHOT</version>
</dependency>
```

然后业务系统配置：

```yaml
data-permission:
  enabled: true
```

SDK 就自动生效。

### 怎么验证成功

写一个测试启动 Spring 容器。

验证：

- `data-permission.enabled=true` 时，SDK 的核心 Bean 存在。
- `data-permission.enabled=false` 时，SDK 的核心 Bean 不存在。

成功标准：

```text
业务系统不需要手动 new SDK 对象
业务系统不需要调用 SDK 初始化方法
只要引入依赖 + 配置开启，SDK 就能自动装配
```

## 第二步：读取配置

### 你要做什么

让 SDK 能读取业务系统里的配置。

例如：

```yaml
data-permission:
  enabled: true
  service-url: http://data-permission-service
  cache:
    expire-seconds: 60
    maximum-size: 10000
  intercept:
    resources:
      security_log:
        resource-type: DORIS
        table-names:
          - normalized_security_log
```

SDK 要能知道：

- 是否开启权限功能
- 权限服务地址是什么
- 缓存多久过期
- 哪些表需要做权限过滤
- 表名对应哪个资源

### 怎么验证成功

写单元测试加载配置。

验证：

```text
enabled 能读到 true
service-url 能读到 http://data-permission-service
normalized_security_log 能映射到 security_log
security_log 的 resource-type 是 DORIS
```

成功标准：

```text
配置写在 yaml 里，SDK 能正确读成 Java 对象
```

## 第三步：拿到当前用户是谁

### 你要做什么

请求进入业务系统时，SDK 要先知道当前用户是谁。

至少要拿到：

- `tenant_id`：租户 ID
- `user_id`：用户 ID

可以先从 Header 里拿，后面再接入真正的认证上下文。

例如：

```text
X-Tenant-Id: t001
X-User-Id: u001
```

### 怎么验证成功

用 Mock 请求测试。

验证：

| 请求内容 | 结果 |
| --- | --- |
| 有 `X-Tenant-Id` 和 `X-User-Id` | 成功提取用户身份 |
| 缺少 `X-Tenant-Id` | 拒绝 |
| 缺少 `X-User-Id` | 拒绝 |

成功标准：

```text
SDK 能知道当前请求属于哪个租户、哪个用户
缺少身份时不能继续执行
```

## 第四步：获取用户权限

### 你要做什么

SDK 拿到用户身份后，要去权限服务查询这个用户有哪些权限。

调用接口：

```text
POST /api/v1/permission/context/query
```

返回类似：

```json
{
  "tenant_id": "t001",
  "user_id": "u001",
  "asset_group_scope": ["ag001", "ag002"],
  "data_level_scope": ["public", "internal"],
  "resource_object": {
    "DORIS": ["security_log"]
  },
  "deny_all": false
}
```

### 怎么验证成功

先不用真的权限服务，可以写一个 Mock Client。

验证：

| 场景 | 结果 |
| --- | --- |
| 权限服务返回正常权限 | SDK 得到权限上下文 |
| 权限服务返回 `deny_all=true` | SDK 拒绝访问 |
| 权限服务 401/403 | SDK 拒绝访问 |
| 权限服务 500 且没有缓存 | SDK 拒绝访问 |

成功标准：

```text
SDK 能根据 tenant_id + user_id 拿到这个用户的数据权限
拿不到权限时默认拒绝
```

## 第五步：把权限放到当前请求里

### 你要做什么

把用户权限保存到 `ThreadLocal`。

这样后面的 SQL 拦截器、字段脱敏、导出控制都可以直接读取当前权限。

流程：

```text
请求开始 -> set 权限上下文
业务执行 -> get 权限上下文
请求结束 -> clear 权限上下文
```

### 怎么验证成功

验证：

| 场景 | 结果 |
| --- | --- |
| 请求开始后读取上下文 | 能读到当前用户权限 |
| 请求结束后读取上下文 | 读不到，已经清理 |
| 连续两个请求 | 不会互相串权限 |

成功标准：

```text
请求 A 的权限不会跑到请求 B 里
请求结束必须清理 ThreadLocal
```

## 第六步：加本地缓存

### 你要做什么

不能每个请求都调用权限服务，否则性能会差。

所以加 Caffeine 本地缓存。

缓存 Key：

```text
tenant_id:user_id:client_app
```

缓存 Value：

```text
PermissionContext
```

### 怎么验证成功

验证：

| 场景 | 结果 |
| --- | --- |
| 第一次请求 | 调用权限服务 |
| 第二次同用户请求 | 直接用缓存 |
| 缓存过期后 | 重新调用权限服务 |
| 权限服务异常但缓存没过期 | 使用缓存 |
| 权限服务异常且没有缓存 | 拒绝 |

成功标准：

```text
同一个用户短时间内多次请求，不会每次都调用权限服务
```

## 第七步：识别哪些表需要权限过滤

### 你要做什么

SDK 要知道哪些数据库表是受控资源。

例如配置：

```yaml
data-permission:
  intercept:
    resources:
      security_log:
        resource-type: DORIS
        table-names:
          - normalized_security_log
```

意思是：

```text
normalized_security_log 这张物理表
属于 security_log 这个权限资源
资源类型是 DORIS
```

### 怎么验证成功

验证：

| SQL 里的表 | 结果 |
| --- | --- |
| `normalized_security_log` | 识别为受控资源 `security_log` |
| `unknown_table` | 不做权限过滤 |
| `normalized_security_log` 但用户没有 `security_log` 权限 | 拒绝 |

成功标准：

```text
SDK 能通过 SQL 里的表名判断是否需要做权限过滤
```

## 第八步：实现 SQL 改写

### 你要做什么

这是一期最核心的步骤。

SDK 拿到原 SQL 后，自动追加权限条件。

原 SQL：

```sql
SELECT * FROM normalized_security_log WHERE event_type = ?
```

权限：

```text
tenant_id = t001
asset_group_scope = ag001, ag002
data_level_scope = public, internal
```

改写后：

```sql
SELECT * FROM normalized_security_log
WHERE event_type = ?
  AND tenant_id = ?
  AND asset_group_id IN (?, ?)
  AND data_level IN (?, ?)
```

参数顺序：

```text
原业务参数
t001
ag001
ag002
public
internal
```

### 怎么验证成功

先写纯单元测试，不连数据库。

验证：

| 原 SQL | 结果 |
| --- | --- |
| `SELECT * FROM normalized_security_log` | 自动加 `WHERE tenant_id = ?` |
| `SELECT * FROM normalized_security_log WHERE event_type = ?` | 自动追加 `AND tenant_id = ?` |
| `SELECT count(*) FROM normalized_security_log` | 能改写 |
| `JOIN` | 拒绝 |
| `UNION` | 拒绝 |
| 子查询 | 拒绝 |

成功标准：

```text
支持的单表 SQL 能正确追加权限条件
不支持的复杂 SQL 必须拒绝，不能放行
```

## 第九步：接入 MyBatis 拦截器

### 你要做什么

前面只是写了 SQL 改写能力。

这一步要让业务 Mapper 真正执行 SQL 前，被 SDK 自动拦截。

流程：

```text
业务 Mapper 发起查询
  -> MyBatis 拦截器拿到 SQL
  -> SDK 判断是否受控资源
  -> SDK 改写 SQL
  -> MyBatis 执行改写后的 SQL
```

### 怎么验证成功

写一个测试 Mapper。

验证：

| 场景 | 结果 |
| --- | --- |
| 查询受控表 | SQL 被改写 |
| 查询未受控表 | SQL 不改写 |
| 没有权限上下文 | 拒绝 |
| 查询受控表但 SQL 是 JOIN | 拒绝 |

成功标准：

```text
业务代码不用手写权限条件
SDK 能在底层自动给 SQL 加权限条件
```

## 第十步：先完成最小 demo

### 你要做什么

不要急着做脱敏、导出、Kafka。

先做一个最小 demo 验证前面链路：

```text
带用户身份请求接口
  -> SDK 提取用户
  -> SDK 获取权限
  -> SDK 放入 ThreadLocal
  -> Mapper 查询受控表
  -> SDK 改写 SQL
  -> 返回结果
```

### 怎么验证成功

最小验收：

| 用例 | 结果 |
| --- | --- |
| 用户有权限查询日志 | SQL 自动加权限条件 |
| 用户没有权限查询日志 | 拒绝 |
| 请求没有用户身份 | 拒绝 |
| 权限服务不可用且无缓存 | 拒绝 |
| 权限服务不可用但有缓存 | 使用缓存 |

成功标准：

```text
权限过滤主链路跑通
这是 SDK 一期最重要的里程碑
```

## 4. 最小闭环完成后再做什么

主链路完成后，再按这个顺序补能力：

## 第十一步：字段脱敏

目标：

```text
接口返回 DTO 时，手机号、邮箱、IP、Token 等字段自动脱敏
```

验证：

| 字段策略 | 结果 |
| --- | --- |
| `SHOW` | 原样返回 |
| `MASK` | 脱敏返回 |
| `HIDE` | 返回 null |
| `DENY` | 拒绝本次响应 |

## 第十二步：导出控制

目标：

```text
导出前判断是否允许导出、是否超过最大条数、是否需要强制脱敏
```

验证：

| 场景 | 结果 |
| --- | --- |
| 没有导出策略 | 拒绝 |
| 不允许导出 | 拒绝 |
| 超过最大条数 | 拒绝 |
| 允许导出且未超限 | 放行 |

## 第十三步：审计上报

目标：

```text
查询、拒绝、导出都生成审计事件
```

验证：

| 行为 | 审计 |
| --- | --- |
| 查询成功 | `QUERY` |
| 无权限 | `DENIED` |
| 导出成功 | `EXPORT` |
| 导出失败 | `DENIED` 或失败 `EXPORT` |

## 第十四步：Kafka 缓存失效

目标：

```text
权限变更后，权限服务发 Kafka 消息，SDK 清理本地缓存
```

验证：

| 场景 | 结果 |
| --- | --- |
| 用户权限变更 | 收到消息后清理该用户缓存 |
| 下次请求 | 重新调用权限服务 |
| Kafka 关闭 | SDK 仍可通过缓存 TTL 过期 |

## 5. 你现在最应该先做哪几件事

建议你先只盯住这 5 件事：

```text
1. Starter 自动装配
2. 配置读取
3. 请求身份提取
4. 权限上下文获取 + 缓存
5. 单表 SQL 自动改写
```

这 5 件事做好，SDK 的核心价值就出来了。

字段脱敏、导出控制、审计、Kafka 都可以放到后面。

## 6. 判断自己有没有做对

你只要能回答下面几个问题，就说明方向是对的：

```text
业务服务引入 SDK 后，是否不用改查询代码？
SDK 是否能知道当前用户是谁？
SDK 是否能拿到当前用户的数据权限？
SDK 是否能自动识别受控表？
SDK 是否能把权限条件加到 SQL 里？
没有权限、权限服务异常、不支持 SQL 时，是否默认拒绝？
```

如果答案都是“是”，一期 SDK 主链路就是对的。


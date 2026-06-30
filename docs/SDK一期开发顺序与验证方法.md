# Data Permission SDK 一期开发顺序与验证方法

## 1. 一期开发顺序总览

一期建议按下面 7 步开发：

```text
1. Starter 骨架：AutoConfiguration + Properties + 开关
2. 身份提取 + ThreadLocal ContextHolder
3. ContextManager + Caffeine 缓存 + 权限服务 Client
4. MyBatis 单表 SQL 改写 MVP
5. 字段脱敏注解 + Jackson 模块
6. 导出 AOP
7. Kafka 失效 + 审计上报
```

这 7 步不要同时做。先把前 4 步跑通，SDK 的核心权限过滤链路就成立了。后 3 步属于增强能力。

## 2. 第一步：Starter 骨架

### 要做什么

先把项目做成一个真正的 Spring Boot Starter。

核心内容：

- `DataPermissionAutoConfiguration`
- `DataPermissionProperties`
- `data-permission.enabled` 总开关
- Spring Boot 自动装配入口
- SDK 核心 Bean 的条件装配

目标效果：

```yaml
data-permission:
  enabled: true
```

业务系统只要引入 SDK 依赖并打开配置，SDK 就能自动加载。

### 怎么验证是否成功

写自动装配测试，不需要启动真实业务服务。

验证 1：开启 SDK 时 Bean 存在。

```text
data-permission.enabled=true
```

期望：

```text
DataPermissionProperties 存在
DataPermissionAutoConfiguration 生效
SDK 核心 Bean 被注册到 Spring 容器
```

验证 2：关闭 SDK 时 Bean 不存在。

```text
data-permission.enabled=false
```

期望：

```text
SDK 核心 Bean 不注册
业务服务可以正常启动
```

推荐测试方式：

```text
ApplicationContextRunner
```

推荐命令：

```bash
./mvnw test
```

### 成功标准

```text
业务系统不需要手动初始化 SDK
业务系统只通过引入依赖 + 配置开关即可启用 SDK
关闭开关后 SDK 不影响业务系统
```

## 3. 第二步：身份提取 + ThreadLocal ContextHolder

### 要做什么

请求进入业务服务时，SDK 要先知道当前用户是谁，然后把用户身份绑定到当前请求线程。

核心内容：

- `PermissionIdentity`
- `PermissionIdentityExtractor`
- `PermissionContextHolder`
- Web 拦截器
- 请求结束后清理 `ThreadLocal`

最少需要提取：

```text
tenant_id
user_id
```

可以先从请求 Header 提取：

```text
X-Tenant-Id: t001
X-User-Id: u001
```

后续再接入真正的认证上下文。

### 怎么验证是否成功

验证 1：请求带完整身份。

输入：

```text
X-Tenant-Id: t001
X-User-Id: u001
```

期望：

```text
SDK 能提取 tenant_id=t001
SDK 能提取 user_id=u001
当前线程能读取到身份上下文
```

验证 2：请求缺少租户。

输入：

```text
X-User-Id: u001
```

期望：

```text
SDK 拒绝请求
不能继续执行后续权限过滤
```

验证 3：请求缺少用户。

输入：

```text
X-Tenant-Id: t001
```

期望：

```text
SDK 拒绝请求
不能继续执行后续权限过滤
```

验证 4：请求结束后清理上下文。

流程：

```text
请求 A 设置 user_id=u001
请求 A 结束
请求 B 设置 user_id=u002
```

期望：

```text
请求 B 不能读到请求 A 的 user_id=u001
```

### 成功标准

```text
SDK 能识别当前请求的租户和用户
缺少 tenant_id 或 user_id 时默认拒绝
ThreadLocal 在请求结束后一定清理
连续请求之间不会串上下文
```

## 4. 第三步：ContextManager + Caffeine 缓存 + 权限服务 Client

### 要做什么

SDK 拿到用户身份后，需要获取这个用户的数据权限。

核心内容：

- `PermissionContext`
- `PermissionContextManager`
- `PermissionServiceClient`
- Caffeine 本地缓存
- 权限服务异常处理

调用权限服务：

```text
POST /api/v1/permission/context/query
```

缓存 Key：

```text
tenant_id:user_id:client_app
```

缓存 Value：

```text
PermissionContext
```

权限服务返回示例：

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

### 怎么验证是否成功

验证 1：第一次请求没有缓存。

期望：

```text
SDK 调用权限服务
权限服务返回 PermissionContext
SDK 写入 Caffeine 缓存
```

验证 2：第二次同用户请求命中缓存。

期望：

```text
SDK 不再调用权限服务
直接返回 Caffeine 中的 PermissionContext
```

验证 3：权限服务返回 `deny_all=true`。

期望：

```text
SDK 默认拒绝
不能继续执行 SQL 查询
```

验证 4：权限服务返回 401 或 403。

期望：

```text
SDK 默认拒绝
```

验证 5：权限服务返回 500，且没有缓存。

期望：

```text
SDK 默认拒绝
```

验证 6：权限服务返回 500，但本地有未过期缓存。

期望：

```text
SDK 使用本地缓存
请求可以继续
```

验证 7：缓存过期。

期望：

```text
SDK 重新调用权限服务
```

### 成功标准

```text
SDK 能根据当前用户获取权限上下文
相同用户短时间内不会重复调用权限服务
权限服务不可用时，有可用缓存则用缓存，无缓存则拒绝
deny_all=true 时一定拒绝
```

## 5. 第四步：MyBatis 单表 SQL 改写 MVP

### 要做什么

这是一期最核心的能力。

业务代码写普通 SQL，SDK 在 MyBatis 执行前自动追加权限条件。

核心内容：

- MyBatis 拦截器
- SQL 资源识别
- JSqlParser SQL 解析
- 单表 `SELECT` 改写
- 简单单表 `COUNT` 改写
- 不支持 SQL 默认拒绝

配置示例：

```yaml
data-permission:
  intercept:
    fail-on-unsupported-sql: true
    resources:
      security_log:
        resource-type: DORIS
        table-names:
          - normalized_security_log
        filter-fields:
          tenant: tenant_id
          asset-group: asset_group_id
          data-level: data_level
```

原 SQL：

```sql
SELECT * FROM normalized_security_log WHERE event_type = ?
```

权限上下文：

```text
tenant_id=t001
asset_group_scope=["ag001", "ag002"]
data_level_scope=["public", "internal"]
```

改写后：

```sql
SELECT * FROM normalized_security_log
WHERE event_type = ?
  AND tenant_id = ?
  AND asset_group_id IN (?, ?)
  AND data_level IN (?, ?)
```

最终参数顺序：

```text
原业务参数
t001
ag001
ag002
public
internal
```

### 怎么验证是否成功

验证 1：无 `WHERE` 的单表查询。

输入：

```sql
SELECT * FROM normalized_security_log
```

期望：

```sql
SELECT * FROM normalized_security_log
WHERE tenant_id = ?
  AND asset_group_id IN (?, ?)
  AND data_level IN (?, ?)
```

验证 2：已有 `WHERE` 的单表查询。

输入：

```sql
SELECT * FROM normalized_security_log WHERE event_type = ?
```

期望：

```text
原 WHERE 保留
后面追加 AND 权限条件
原业务参数排在前面
权限参数排在后面
```

验证 3：简单 `COUNT`。

输入：

```sql
SELECT count(*) FROM normalized_security_log
```

期望：

```text
能正常追加权限条件
```

验证 4：查询未配置表。

输入：

```sql
SELECT * FROM unknown_table
```

期望：

```text
SDK 不改写
```

验证 5：查询已配置表，但用户没有该资源权限。

期望：

```text
SDK 默认拒绝
```

验证 6：复杂 SQL。

输入：

```sql
SELECT * FROM a JOIN b ON a.id = b.a_id
```

期望：

```text
SDK 默认拒绝
```

需要拒绝的 SQL：

```text
JOIN
UNION
子查询
CTE / WITH
INSERT
UPDATE
DELETE
```

### 成功标准

```text
业务 Mapper 不需要手写权限条件
受控表查询会被自动追加权限过滤
未受控表不受影响
不支持的受控 SQL 不允许静默放行
所有权限值都通过参数化方式追加
```

## 6. 第五步：字段脱敏注解 + Jackson 模块

### 要做什么

SQL 权限过滤解决“能看哪些数据”。

字段脱敏解决“字段能不能看明文”。

核心内容：

- `@PermissionResource`
- `@PermissionField`
- Jackson 序列化扩展
- 字段策略匹配
- 脱敏规则

示例：

```java
@PermissionResource(type = "DORIS", id = "security_log", scene = "DETAIL")
public LogDetailVO detail(LogQuery query) {
    return logService.detail(query);
}
```

```java
public class LogDetailVO {

    @PermissionField("src_ip")
    private String sourceIp;

    @PermissionField("raw_log")
    private String rawLog;
}
```

字段策略：

```text
SHOW：原样返回
MASK：脱敏返回
HIDE：返回 null
DENY：拒绝本次响应
```

### 怎么验证是否成功

验证 1：`SHOW`。

输入字段：

```text
phone=13812345678
```

期望：

```text
13812345678
```

验证 2：`MASK`。

输入字段：

```text
phone=13812345678
```

期望：

```text
手机号被脱敏
```

验证 3：`HIDE`。

输入字段：

```text
raw_log=原始日志内容
```

期望：

```text
raw_log=null
```

验证 4：`DENY`。

期望：

```text
SDK 抛出权限异常
本次响应被拒绝
```

验证 5：同名字段在不同资源下策略不同。

期望：

```text
按 resource_type + resource_id + scene + field_name 精确匹配
不能只按字段名全局匹配
```

### 成功标准

```text
接口返回对象经过 Jackson 序列化时自动执行字段策略
DTO 有注解时按注解字段名匹配
DTO 无注解时按输出字段名兜底
敏感字段不会在无权限时明文返回
```

## 7. 第六步：导出 AOP

### 要做什么

导出是高风险动作，不能只靠查询权限。

核心内容：

- `@ExportGuard`
- 导出 AOP 拦截
- 导出策略匹配
- 最大导出条数校验
- 强制脱敏标记

示例：

```java
@ExportGuard(resourceType = "DORIS", resourceId = "security_log")
public void export(LogQuery query) {
    long count = logService.countForExport(query);
    DataPermissionExport.checkRows(count);
    logService.export(query);
}
```

一期规则：

```text
没有导出策略：拒绝
is_allowed=false：拒绝
max_rows=0：拒绝
预计导出条数 > max_rows：拒绝
need_approval=true：一期拒绝
force_mask=true：导出时强制脱敏
```

### 怎么验证是否成功

验证 1：没有导出策略。

期望：

```text
拒绝导出
```

验证 2：`is_allowed=false`。

期望：

```text
拒绝导出
```

验证 3：`max_rows=0`。

期望：

```text
拒绝导出
```

验证 4：超过最大条数。

输入：

```text
max_rows=5000
预计导出条数=6000
```

期望：

```text
拒绝导出
```

验证 5：允许导出。

输入：

```text
is_allowed=true
max_rows=5000
预计导出条数=100
```

期望：

```text
放行导出
```

验证 6：`force_mask=true`。

期望：

```text
导出结果强制走脱敏策略
```

### 成功标准

```text
导出方法被 AOP 拦截
无策略、无权限、超限、需要审批时都拒绝
允许导出且未超限时才放行
导出场景可以强制脱敏
```

## 8. 第七步：Kafka 失效 + 审计上报

### 要做什么

最后再补缓存失效和审计。

这一步不是主链路第一优先级，但生产环境需要。

核心内容：

- Kafka 权限失效消费者
- Kafka 审计生产者
- `AuditEvent`
- `AuditReporter`
- 缓存清理接口

缓存失效：

```text
权限服务发出用户权限变更事件
SDK 收到 Kafka 消息
SDK 清理本地 Caffeine 缓存
用户下次请求重新拉取权限上下文
```

审计上报：

```text
QUERY：查询成功
DENIED：权限拒绝
EXPORT：导出行为
```

### 怎么验证是否成功

验证 1：用户权限失效。

前置：

```text
Caffeine 中已有 t001:u001:app-a 的权限缓存
```

输入 Kafka 消息：

```json
{
  "tenant_id": "t001",
  "user_id": "u001",
  "client_app": "app-a"
}
```

期望：

```text
该用户缓存被清理
下一次请求重新调用权限服务
```

验证 2：全量失效。

期望：

```text
清空全部 Caffeine 缓存
```

验证 3：查询成功审计。

期望：

```text
生成 action=QUERY,result=ALLOWED 的审计事件
```

验证 4：权限拒绝审计。

期望：

```text
生成 action=DENIED,result=DENIED 的审计事件
```

验证 5：导出审计。

期望：

```text
导出成功生成 EXPORT 成功审计
导出失败生成 EXPORT 失败审计或 DENIED 审计
```

验证 6：Kafka 发送失败。

期望：

```text
业务主流程不失败
SDK 只记录错误日志
```

### 成功标准

```text
权限变更后 SDK 能清理本地缓存
查询、拒绝、导出都有审计事件
审计发送失败不能影响业务请求
Kafka 关闭时 SDK 仍能依赖缓存 TTL 工作
```

## 9. 一期最重要的验收目标

前 4 步完成后，必须能跑通这个场景：

```text
业务服务引入 SDK
配置 data-permission.enabled=true
请求带 tenant_id 和 user_id
SDK 获取用户权限上下文
业务 Mapper 查询 normalized_security_log
SDK 自动把 SQL 改写为带权限条件的 SQL
数据库只返回用户有权限的数据
```

最终判断标准：

```text
业务代码不手写权限 SQL
SDK 自动完成权限过滤
没有权限时默认拒绝
权限上下文不可用时默认拒绝
受控资源复杂 SQL 不支持时默认拒绝
```

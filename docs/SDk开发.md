# Data Permission SDK 开发设计

## 1. 文档定位

本文档用于指导数据权限 SDK 的一期开发落地，重点说明 SDK 在业务服务侧需要提供哪些能力、如何接入、如何执行权限控制和权限过滤，以及一期明确不做哪些复杂能力。

SDK 的核心定位是：**权限执行面组件**。

权限服务负责策略管理、授权关系维护、权限上下文计算和审计落库；SDK 内嵌到业务服务中，负责在请求链路内执行权限上下文获取、SQL 权限过滤、字段脱敏、导出控制、审计上报和缓存失效处理。

## 2. 一期设计结论

| 设计项 | 一期结论 |
| --- | --- |
| SDK 形态 | Java Spring Boot Starter |
| 缓存方案 | 只使用 Caffeine 本地缓存，不引入 Redis |
| 权限上下文来源 | SDK 调用权限服务 `/api/v1/permission/context/query` 获取打平后的上下文 |
| 权限失效 | 权限服务发送 Kafka 失效事件，SDK 清理本地 Caffeine 缓存 |
| SQL 改写 | 一期只支持单表 `SELECT` 和简单单表 `COUNT` |
| 复杂 SQL | 一期不支持 `JOIN`、`UNION`、子查询、CTE、复杂分页 COUNT 自动改写 |
| 字段策略 | 采用“资源上下文 + 字段映射”的方式，DTO 注解优先，字段名兜底 |
| 导出超限 | 一期直接拒绝，不接审批流 |
| 命名规范 | 统一 `resource_type` 和 `resource_id`，贯穿策略、上下文、SDK 配置和审计 |
| 安全策略 | 默认拒绝，权限上下文不可用时不放行 |

## 3. SDK 总体职责

SDK 负责以下能力：

- 请求进入业务服务时，提取可信用户身份并初始化权限上下文。
- 使用本地 Caffeine 缓存权限上下文，缓存未命中时回源权限服务。
- 将权限上下文绑定到当前请求线程，供 SQL 改写、字段脱敏、导出控制和审计使用。
- 拦截 MyBatis/JDBC SQL，在 SQL 执行前追加租户、部门、资产组、数据等级等过滤条件。
- 在接口返回序列化阶段，根据字段策略执行明文、脱敏、隐藏或拒绝。
- 在导出前检查导出开关、最大导出条数、是否强制脱敏。
- 对查询、拒绝、导出等行为生成审计事件，并异步上报 Kafka。
- 监听权限上下文失效事件，主动清理本地缓存。

SDK 不负责以下能力：

- 不负责权限策略管理。
- 不负责用户、角色、部门、用户组、资产组等主数据管理。
- 不负责复杂授权关系计算。
- 不负责审批流。
- 不负责修复业务 SQL 语义。
- 不负责前端权限展示控制。

## 4. 总体执行链路

```text
[客户端请求]
    |
    v
[业务服务 Web Interceptor]
    |
    | 提取 tenant_id / user_id / dept_id / role_id / trace_id / client_ip
    v
[SDK Context Manager]
    |
    | 先查 Caffeine 本地缓存
    | 未命中时调用权限服务获取权限上下文
    v
[SDK Context Holder]
    |
    | ThreadLocal 绑定当前请求权限上下文
    v
[业务 Controller / Service / Mapper]
    |
    v
[SQL Rewrite Interceptor]
    |
    | 识别受控资源并追加权限过滤条件
    v
[PostgreSQL / Doris]
    |
    v
[响应 DTO / VO 序列化]
    |
    | 字段脱敏 / 隐藏 / 拒绝
    v
[Audit Reporter]
    |
    | 异步发送审计事件
    v
[返回客户端]
```

请求结束后，SDK 必须清理 ThreadLocal，避免线程复用导致上下文串扰。

## 5. 核心模块设计

### 5.1 Starter Auto Configuration

SDK 以 Spring Boot Starter 形式交付，业务服务引入依赖后，通过配置开关启用。

自动装配内容：

- Web 请求拦截器。
- 权限上下文管理器。
- Caffeine 本地缓存。
- MyBatis/JDBC SQL 拦截器。
- Jackson 字段脱敏模块。
- 导出控制 AOP。
- Kafka 权限失效事件消费者。
- Kafka 审计事件生产者。

推荐配置：

```yaml
data-permission:
  enabled: true
  service-url: http://data-permission-service

  cache:
    expire-seconds: 60
    maximum-size: 10000

  kafka:
    enabled: true
    invalidate-topic: auth-context-invalidate
    audit-topic: data-permission-audit-topic

  intercept:
    fail-on-unsupported-sql: true
    resources:
      security_log:
        resource-type: DORIS
        table-names:
          - normalized_security_log
        filter-fields:
          tenant: tenant_id
          dept: dept_id
          asset-group: asset_group_id
          data-level: data_level

      alert_event:
        resource-type: POSTGRESQL
        table-names:
          - t_alert_event
        filter-fields:
          tenant: tenant_id
          dept: dept_id
          asset-group: asset_group_id
          data-level: data_level
```

### 5.2 Request Identity Extractor

请求身份提取模块负责从业务服务的可信上下文中提取身份信息。

必需字段：

| 字段 | 说明 |
| --- | --- |
| `tenant_id` | 租户 ID |
| `user_id` | 用户 ID |
| `dept_id` | 当前用户所属部门 ID |
| `role_id` | 当前用户角色 ID |

辅助字段：

| 字段 | 说明 |
| --- | --- |
| `trace_id` | 链路追踪 ID |
| `client_ip` | 客户端 IP |
| `client_app` | 当前业务服务标识 |

设计要求：

- 身份信息必须来自服务端可信认证结果，不能直接相信前端请求体。
- 无法识别 `tenant_id` 或 `user_id` 时，SDK 默认拒绝。
- 提取完成后交给 Context Manager 获取权限上下文。

### 5.3 Context Holder

Context Holder 用于保存当前请求的权限上下文。

Java 一期使用 `ThreadLocal`：

```text
PermissionContextHolder.set(context)
PermissionContextHolder.get()
PermissionContextHolder.clear()
```

使用要求：

- Web 拦截器在请求进入时绑定上下文。
- 请求结束时必须执行 `clear()`。
- SQL 改写、字段脱敏、导出控制、审计上报都只能从 Context Holder 获取当前上下文。
- 不允许各模块重复解析 token 或重复调用权限服务。

### 5.4 Context Manager

Context Manager 负责权限上下文获取、缓存、失效和异常处理。

获取顺序：

```text
1. 根据 tenant_id + user_id + client_app 生成 cache key
2. 查询 Caffeine 本地缓存
3. 命中则返回 PermissionContext
4. 未命中则调用权限服务 /api/v1/permission/context/query
5. 权限服务返回成功后写入 Caffeine
6. 返回当前请求线程
```

缓存 key：

```text
tenant_id:user_id:client_app
```

缓存 value：

```text
PermissionContext
```

缓存策略：

- 一期只使用 Caffeine 本地缓存。
- 不引入 Redis。
- 默认 TTL 建议 60 秒。
- `expires_at` 早于当前时间时视为无效。
- Kafka 失效事件优先主动清理缓存。
- Kafka 消息丢失时依赖 TTL 最终过期。

异常处理：

| 场景 | SDK 行为 |
| --- | --- |
| 上下文接口成功 | 写入缓存并放行到后续链路 |
| 上下文接口返回 `deny_all=true` | 默认拒绝 |
| 上下文接口 401/403 | 默认拒绝 |
| 上下文接口 404 | 按无权限处理 |
| 上下文接口 409 | 重新拉取一次上下文 |
| 上下文接口 429 | 有缓存则使用缓存，无缓存则拒绝 |
| 上下文接口 500/503 | 有缓存则使用未过期缓存，无缓存则拒绝 |
| 本地上下文为空 | 默认拒绝 |

### 5.5 Permission Context Model

SDK 内部统一使用打平后的权限上下文模型。

```json
{
  "tenant_id": "t001",
  "user_id": "u001",
  "dept_scope": ["d001", "d002"],
  "asset_group_scope": ["ag001", "ag002"],
  "data_level_scope": ["public", "internal"],
  "resource_object": {
    "DORIS": ["security_log"],
    "POSTGRESQL": ["alert_event", "asset"]
  },
  "field_policies": [
      {
        "field_name": "phone",
        "permission_type": "MASK",
        "mask_regex": "PHONE",
        "scene": ["DETAIL"]
      },
      {
        "field_name": "raw_log",
        "permission_type": "HIDE",
        "mask_regex": null,
        "scene": ["DETAIL","EXPORT"]
      }
    ],
    "export_policies": [
      {
        "resource_type": "doris",
        "resource_id": "security_log",
        "is_allowed": true,
        "max_rows": 5000,
        "need_approval": false
      }
    ],
  "deny_all": false,
  "version": 12,
  "expires_at": "2026-06-29T18:00:00+08:00"
}
```

核心判断规则：

- `deny_all=true` 时，所有访问直接拒绝。
- `resource_object` 不包含当前资源时，当前资源无权限。
- `dept_scope=["*"]` 表示全部部门。
- `asset_group_scope=["*"]` 表示全部资产组。
- `data_level_scope` 为空时，不允许访问任何数据等级。
- 任何权限维度为空且没有通配符时，均按无权限处理。

## 6. resource_type 和 resource_id 规范

### 6.1 resource_type

`resource_type` 表示资源所属的数据引擎或资源大类。一期统一使用大写枚举：

| 标准值 | 说明 |
| --- | --- |
| `DORIS` | Doris 查询资源 |
| `POSTGRESQL` | PostgreSQL 查询资源 |

SDK 可兼容历史小写入参，例如 `doris`、`postgre_sql`，但内部模型和新增配置统一归一化为 `DORIS`、`POSTGRESQL`。

### 6.2 resource_id

`resource_id` 表示业务逻辑资源，不直接等同于物理表名（一期与主物理表名保持一致）。

命名规则：

- 使用小写下划线。
- 使用业务语义命名。
- 不使用数据库名、schema 名或环境名。
- 不随物理表拆分变化而变化。

示例：

| resource_id | 说明 |
| --- | --- |
| `security_log` | 安全日志 |
| `alert_event` | 告警事件 |
| `asset` | 资产 |
| `report` | 报表 |

### 6.3 物理表映射

物理表名只存在于 SDK 配置中：

```yaml
data-permission:
  intercept:
    resources:
      security_log:
        resource-type: DORIS
        table-names:
          - normalized_security_log
```

同一个 `resource_id` 必须贯穿以下对象：

- 数据权限主策略。
- 字段策略。
- 导出策略。
- 权限上下文。
- SDK 资源映射配置。
- 审计事件。

## 7. SQL 权限过滤设计

### 7.1 目标

SQL 权限过滤用于在业务 SQL 执行前，自动追加服务端可信权限条件，避免业务代码手工拼接权限逻辑。

一期过滤维度：

- 租户：`tenant_id`
- 部门：`dept_id`
- 资产组：`asset_group_id`
- 数据等级：`data_level`

### 7.2 一期支持范围

支持：

- 单表 `SELECT`
- 简单单表 `COUNT`
- 原 SQL 已有 `WHERE` 时追加 `AND`
- 原 SQL 无 `WHERE` 时新增 `WHERE`
- PostgreSQL 和 Doris 的普通查询过滤

暂不支持：

- `JOIN`
- `UNION`
- 子查询
- CTE / `WITH`
- 多表聚合
- 嵌套查询
- 复杂分页 COUNT SQL 自动改写
- 非查询 SQL 的自动改写，例如 `INSERT`、`UPDATE`、`DELETE`

### 7.3 不支持 SQL 的处理原则

命中受控资源，但 SQL 结构不在一期支持范围内时，SDK 必须默认拒绝。

```text
受控资源 + 不支持 SQL = 拒绝
```

禁止静默跳过权限过滤后继续执行原始 SQL。

可选处理方式：

- 抛出权限异常，由业务服务返回 403。
- 改写为无结果 SQL，例如 `WHERE 1 = 0`。

一期建议默认抛出权限异常，并记录 `DENIED` 审计。

### 7.4 SQL 改写示例

原 SQL：

```sql
SELECT * FROM normalized_security_log WHERE event_type = ?
```

权限上下文：

```json
{
  "tenant_id": "t001",
  "asset_group_scope": ["ag001", "ag002"],
  "data_level_scope": ["public", "internal"]
}
```

改写后：

```sql
SELECT * FROM normalized_security_log
WHERE event_type = ?
  AND tenant_id = ?
  AND asset_group_id IN (?, ?)
  AND data_level IN (?, ?)
```

参数追加顺序：

```text
原业务参数
t001
ag001
ag002
public
internal
```

权限为空时：

```sql
SELECT * FROM normalized_security_log
WHERE 1 = 0
```

### 7.5 改写要求

- 必须基于 SQL AST 改写，不允许字符串拼接权限条件。
- Java 一期使用 JSqlParser。
- 所有权限值必须使用参数化占位符。
- 不允许前端或业务入参覆盖 SDK 注入的 `tenant_id`。
- 不允许业务 SQL 中已有的权限字段抵消 SDK 的权限条件。
- 管理员 `["*"]` 权限不生成无意义的 `IN` 条件。
- 改写完成后记录参数化过滤片段用于审计。

### 7.6 资源识别

SQL 拦截器根据物理表名识别资源。

```text
normalized_security_log -> security_log -> DORIS
t_alert_event -> alert_event -> POSTGRESQL
```

如果 SQL 访问的表没有配置在 `data-permission.intercept.resources` 中，默认不纳入 SDK SQL 改写范围。

如果访问的是已配置受控表，但当前用户上下文不包含该 `resource_id`，SDK 默认拒绝。

## 8. 字段脱敏设计

### 8.1 设计结论

字段策略采用“资源上下文 + 字段映射”的方式。

匹配优先级：

```text
1. 当前接口或方法声明的 resource_type + resource_id + scene
2. DTO 字段上的 @PermissionField 映射名
3. Jackson 输出字段名兜底
4. 匹配权限上下文中的 field_policies
```

不建议只按字段名全局动态匹配。原因是不同资源里可能存在相同字段名，但敏感级别和展示规则不同。

### 8.2 资源上下文声明

接口或方法需要声明当前返回数据属于哪个权限资源。

示例：

```java
@PermissionResource(type = "DORIS", id = "security_log", scene = "DETAIL")
public LogDetailVO detail(LogQuery query) {
    return logService.detail(query);
}
```

也可以通过 AOP、Controller 注解或上下文 API 设置：

```java
DataPermissionScene.set("DORIS", "security_log", "DETAIL");
```

### 8.3 字段映射声明

DTO 字段建议使用注解声明逻辑字段名：

```java
public class LogDetailVO {

    @PermissionField("src_ip")
    private String sourceIp;

    @PermissionField("raw_log")
    private String rawLog;
}
```

如果没有 `@PermissionField`，SDK 使用 Jackson 输出字段名兜底，例如 `sourceIp` 或 `source_ip`。

### 8.4 字段策略匹配维度

字段策略必须同时匹配以下维度：

```text
tenant_id
resource_type
resource_id
scene
field_name
```

字段策略动作：

| permission_type | SDK 行为 |
| --- | --- |
| `show` | 明文返回 |
| `mask` | 按脱敏规则返回 |
| `hide` | 字段返回 `null` 或不序列化 |
| `deny` | 拒绝本次响应或抛出权限异常 |

一期建议：

- `show`：原样返回。
- `mask`：脱敏后返回。
- `hide`：返回 `null`。
- `deny`：抛出权限异常，并记录 `DENIED` 审计。

### 8.5 脱敏规则

一期内置脱敏类型：

| mask_rule_type | 说明 |
| --- | --- |
| `PHONE` | 手机号脱敏 |
| `EMAIL` | 邮箱脱敏 |
| `ID_CARD` | 身份证号脱敏 |
| `IP` | IP 地址脱敏 |
| `TOKEN` | Token 或密钥脱敏 |
| `REGEX` | 自定义正则脱敏 |

导出场景如果 `force_mask=true`，即使详情页允许明文，导出也必须强制脱敏。

## 9. 导出控制设计

### 9.1 目标

导出控制用于防止用户批量导出无权限数据或超量导出敏感数据。

一期导出策略：

- 不允许导出时直接拒绝。
- `max_rows=0` 时直接拒绝。
- 预计导出条数大于 `max_rows` 时直接拒绝。
- `need_approval=true` 时一期直接拒绝，不接审批流。
- `force_mask=true` 时导出结果强制脱敏。

### 9.2 导出流程

```text
1. 业务触发导出接口
2. Export Guard 获取当前 PermissionContext
3. 根据 resource_type + resource_id 查找 export_policies
4. 判断 is_allowed
5. 判断 max_rows
6. 业务提供预计导出条数
7. 超限则拒绝
8. 放行导出
9. 导出数据经过字段脱敏
10. 发送 EXPORT 审计事件
```

### 9.3 导出注解

示例：

```java
@ExportGuard(resourceType = "DORIS", resourceId = "security_log")
public void export(LogQuery query) {
    long count = logService.countForExport(query);
    DataPermissionExport.checkRows(count);
    logService.export(query);
}
```

一期不建议 SDK 自动把任意查询 SQL 改写为 COUNT SQL。导出预估条数由业务服务显式提供，避免复杂 SQL 推导错误。

### 9.4 导出拒绝场景

| 场景 | 行为 |
| --- | --- |
| 未找到导出策略 | 拒绝 |
| `is_allowed=false` | 拒绝 |
| `max_rows=0` | 拒绝 |
| 预计条数超过 `max_rows` | 拒绝 |
| `need_approval=true` | 一期拒绝 |
| 上下文不可用 | 拒绝 |

所有拒绝都需要记录 `DENIED` 或 `EXPORT` 失败审计。

## 10. 审计设计

### 10.1 审计事件范围

SDK 一期记录以下事件：

| action | 触发场景 |
| --- | --- |
| `QUERY` | SQL 被 SDK 注入权限过滤并成功执行 |
| `DETAIL` | 详情查询触发字段策略 |
| `EXPORT` | 导出请求通过预检并执行 |
| `DENIED` | 权限上下文不可用、无资源权限、导出超限、不支持 SQL 等拒绝场景 |

### 10.2 审计事件结构

```json
{
  "trace_id": "trc-20260629-0901",
  "tenant_id": "t001",
  "user_id": "u001",
  "action": "QUERY",
  "resource_type": "DORIS",
  "resource_id": "security_log",
  "result": "ALLOWED",
  "filter_sql": "tenant_id = ? AND asset_group_id IN (?, ?)",
  "estimated_rows": 0,
  "rows_affected": 20,
  "client_ip": "10.1.1.10",
  "created_at": "2026-06-29T14:30:00+08:00"
}
```

### 10.3 上报方式

- SDK 异步发送 Kafka 消息到 `data-permission-audit-topic`。
- 审计发送失败不能阻塞主业务请求。
- SDK 本地记录错误日志，便于排查审计发送失败。
- 一期不要求 SDK 本地落盘补偿。

## 11. Kafka 权限失效设计

### 11.1 Topic

```text
auth-context-invalidate
```

### 11.2 消息格式

```json
{
  "event_id": "evt-inv-20260629-0001",
  "event_type": "PERMISSION_CONTEXT_INVALIDATE",
  "users": [
    { "tenant_id": "t001", "user_id": "u001" },
    { "tenant_id": "t001", "user_id": "u002" }
  ],
  "version": 29,
  "reason": "policy_updated",
  "occurred_at": "2026-06-29T15:45:00+08:00"
}
```

### 11.3 SDK 处理规则

- 根据 `tenant_id + user_id` 删除匹配的本地缓存。
- 如果缓存 key 包含 `client_app`，则删除该用户在当前业务服务下的所有匹配 key。
- 如果收到的 `version` 小于等于本地上下文版本，可以忽略。
- 消息解析失败只记录错误日志，不影响业务请求。

## 12. 默认拒绝策略

以下场景必须拒绝：

- 无法识别用户身份。
- `tenant_id` 为空。
- `user_id` 为空。
- 权限上下文获取失败且无未过期本地缓存。
- 权限上下文 `deny_all=true`。
- 当前资源不在 `resource_object` 中。
- 当前过滤维度为空且没有 `["*"]`。
- 受控资源 SQL 结构不支持。
- 导出策略不存在。
- 导出超过最大条数。
- 字段策略为 `deny`。

默认拒绝不能静默放行。

## 13. 异常与返回建议

SDK 内部异常建议统一转换为业务可识别异常：

| 异常 | 建议 HTTP 状态码 | 说明 |
| --- | --- | --- |
| `PermissionContextUnavailableException` | 403 | 权限上下文不可用 |
| `PermissionDeniedException` | 403 | 无资源访问权限 |
| `UnsupportedPermissionSqlException` | 403 | 当前 SQL 不支持安全改写 |
| `ExportPermissionDeniedException` | 403 | 不允许导出 |
| `ExportRowsExceededException` | 403 | 导出条数超限 |
| `FieldPermissionDeniedException` | 403 | 字段禁止访问 |

业务服务可通过统一异常处理器转换响应。

## 14. 开发包结构建议

```text
data-permission-sdk
  ├── autoconfigure
  │   └── DataPermissionAutoConfiguration
  ├── config
  │   └── DataPermissionProperties
  ├── context
  │   ├── PermissionContext
  │   ├── PermissionContextHolder
  │   └── PermissionContextManager
  ├── identity
  │   └── RequestIdentityExtractor
  ├── cache
  │   └── PermissionContextCache
  ├── sql
  │   ├── SqlRewriteInterceptor
  │   ├── SqlRewriteEngine
  │   ├── ResourceMappingRegistry
  │   └── PermissionFilterBuilder
  ├── masking
  │   ├── PermissionResource
  │   ├── PermissionField
  │   ├── DataMaskingEngine
  │   └── PermissionJacksonModule
  ├── export
  │   ├── ExportGuard
  │   ├── ExportGuardAspect
  │   └── DataPermissionExport
  ├── audit
  │   ├── AuditEvent
  │   └── AuditReporter
  ├── kafka
  │   └── PermissionInvalidateListener
  └── exception
      └── DataPermissionException
```

## 15. 一期验收标准

### 15.1 上下文与缓存

- 能从权限服务获取上下文。
- 能写入和读取 Caffeine 本地缓存。
- 缓存过期后能重新回源。
- 收到 Kafka 失效事件后能清理本地缓存。
- 上下文不可用时默认拒绝。

### 15.2 SQL 权限过滤

- 单表 `SELECT` 能追加租户、部门、资产组、数据等级条件。
- 单表 `COUNT` 能追加相同过滤条件。
- 权限为空时返回空结果或拒绝。
- 受控资源遇到 `JOIN`、`UNION`、子查询时拒绝。
- 改写使用参数化条件。

### 15.3 字段脱敏

- 能根据 `resource_type + resource_id + scene + field_name` 匹配字段策略。
- `@PermissionField` 优先于输出字段名。
- 支持 `show`、`mask`、`hide`、`deny`。
- 导出场景支持强制脱敏。

### 15.4 导出控制

- 未配置导出权限时拒绝。
- 超过 `max_rows` 时拒绝。
- `need_approval=true` 时一期拒绝。
- 导出成功和失败都能记录审计。

### 15.5 审计

- 查询成功能记录 `QUERY`。
- 详情脱敏能记录必要审计摘要。
- 导出能记录 `EXPORT`。
- 权限拒绝能记录 `DENIED`。
- 审计异步发送失败不影响主请求。

## 16. 后续演进

一期完成后，可以按以下方向增强：

- 支持更复杂 SQL，例如 `JOIN`、`UNION`、子查询、CTE。
- 支持分页查询自动 COUNT 改写。
- 支持审批流，对接导出审批。
- 支持本地审计失败补偿。
- 支持更多资源类型。
- 抽象多语言 SDK 协议，演进 Rust SDK。
- 增加策略模拟和 SDK 调试模式。

## 17. 总结

一期 SDK 的核心目标不是覆盖所有复杂场景，而是先把权限执行闭环落地：

- 通过 Caffeine 缓存权限上下文。
- 通过 ThreadLocal 保证一次请求内身份口径一致。
- 通过 SQL AST 对单表查询追加权限过滤。
- 通过字段策略完成响应脱敏。
- 通过导出控制防止批量越权。
- 通过审计上报形成合规追踪。

所有不确定、不可安全改写、上下文不可用的场景，一期均采用默认拒绝策略。

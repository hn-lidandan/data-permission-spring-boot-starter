# SDK 开发进度

更新时间：2026-07-01

## 当前状态

当前 SDK 已完成第一阶段骨架和权限上下文获取主链路。下面按开发流程线列进度，已做完的打 `√`。

## 开发流程线

- [x] 1. Starter 骨架
  - 先把 SDK 变成 starter 形态，提供 `AutoConfiguration`、`Properties` 和总开关。
- [x] 2. 身份提取
  - 从请求 Header 提取 `tenant_id`、`user_id`、`dept_id`、`role_id`，作为权限查询输入。
- [x] 3. 权限服务 Client
  - 由 SDK 自己发 `POST` 请求调用权限服务，不让业务服务手写调用逻辑。
- [x] 4. 权限上下文模型
  - 定义 `PermissionContext`、`FieldPolicy`、`ExportPolicy`，并统一使用 `LocalDateTime`。
- [x] 5. 本地缓存与 ContextManager
  - 用 Caffeine 缓存上下文，未命中时回源权限服务，命中后直接复用。
- [x] 6. Web 接入
  - 通过 Web 拦截器把上下文绑定到当前请求线程，结束后清理 `ThreadLocal`。
- [x] 7. 接口契约对齐
  - 已按真实返回样例适配微秒时间、`allowed/is_allowed` 双字段和嵌套 `data` 响应。
- [ ] 8. 真实业务联调
  - 在真实业务服务里接入 starter，验证整条请求链路是否能稳定拿到上下文。
- [ ] 9. SQL 权限过滤
  - 基于上下文对查询 SQL 做范围过滤，限制部门、资产组、数据等级等访问。
- [ ] 10. 字段脱敏
  - 按 `field_policies` 实现 `SHOW / MASK / HIDE / DENY`。
- [ ] 11. 导出控制
  - 按 `export_policies` 控制导出行数、审批和强制脱敏。
- [ ] 12. Kafka 失效
  - 权限变更后通过消息清理本地缓存。
- [ ] 13. 审计上报
  - 记录拒绝、导出和敏感字段访问事件。
- [ ] 14. 示例工程与文档
  - 提供一个最小接入示例和常见问题排查说明。

已经从普通 Spring Boot 应用调整为 Spring Boot Starter 形态：

- 使用 Spring Boot `3.5.8`，避免 IDEA 旧版 JUnit Runner 与 Spring Boot 4 / JUnit 6 不兼容。
- 删除原应用启动类和默认 `application.yaml`。
- 通过 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 注册自动装配类。
- SDK 默认关闭，业务服务需要显式配置 `data-permission.enabled=true` 才启用。

当前测试结果：

```bash
./mvnw test
```

结果：`23` 个测试全部通过。

## 已完成

### 1. Starter 自动装配骨架

已完成类：

- `DataPermissionPropertiesAutoConfiguration`
- `DataPermissionAutoConfiguration`
- `DataPermissionWebAutoConfiguration`

已完成能力：

- `data-permission.enabled` 总开关。
- `data-permission.service-url` 启动期必填校验。
- 缓存参数校验。
- HTTP Client 超时参数校验。
- Web 自动装配按条件启用。
- SDK 内置 `PermissionServiceClient` 作为主客户端，由 SDK 自己调用权限服务。

当前核心配置：

```yaml
data-permission:
  enabled: true
  service-url: http://localhost:8081/data-auth-center/data-permissions/context
  client-app: log-service
  client:
    connect-timeout-millis: 3000
    request-timeout-millis: 5000
  cache:
    expire-seconds: 60
    maximum-size: 10000
```

### 2. 权限上下文模型

已完成类：

- `PermissionContext`
- `FieldPolicy`
- `ExportPolicy`
- `PermissionType`
- `FlexibleLocalDateTimeDeserializer`

已适配真实返回：

- `expires_at` 使用 `LocalDateTime`。
- 支持 `2026-07-01T11:20:48.762675` 这种微秒时间。
- 兼容旧样例里的 `2026-06-29T18:00:00+08:00`，SDK 会取本地时间部分。
- `export_policies` 中接口契约字段是 `is_allowed`。
- 真实返回里如果同时带 `allowed` 和 `is_allowed`，SDK 也能解析。

真实返回样例已进入测试覆盖。

### 3. 身份提取

已完成类：

- `PermissionIdentity`
- `PermissionIdentityExtractor`
- `HeaderPermissionIdentityExtractor`

当前默认从 Header 读取身份：

| 身份字段 | 默认 Header | 是否必填 |
| --- | --- | --- |
| `tenant_id` | `X-Tenant-Id` | 是 |
| `user_id` | `X-User-Id` | 是 |
| `dept_id` | `X-Dept-Id` | 是 |
| `role_id` | `X-Role-Id` | 是 |
| `trace_id` | `X-Trace-Id` | 否 |
| `client_app` | `X-Client-App` | 否，仅用于 SDK 本地缓存 key |

说明：

- 权限服务上下文接口请求体只发送 `tenant_id`、`user_id`、`dept_id`、`role_id`。
- `client_app` 不发送给权限服务，只参与 SDK 本地缓存 key，避免不同业务服务上下文互相污染。
- 缺少任一必填身份字段时，SDK 默认拒绝。

### 4. 权限服务 HTTP Client

已完成类：

- `PermissionServiceClient`
- `HttpPermissionServiceClient`
- `DenyAllPermissionServiceClient`

当前主链路：

1. Web 请求进入业务服务。
2. SDK Web 拦截器提取身份。
3. `PermissionContextManager` 先查本地缓存。
4. 缓存未命中时，SDK 内置 `HttpPermissionServiceClient` 调用权限服务。
5. 权限服务返回上下文后写入本地缓存。
6. 当前请求通过 `PermissionContextHolder` 获取上下文。

权限服务接口契约：

```http
POST /data-auth-center/data-permissions/context
Content-Type: application/json
```

请求体：

```json
{
  "tenant_id": "t001",
  "user_id": "u001",
  "dept_id": "d001",
  "role_id": "r001"
}
```

已完成处理：

- HTTP `200`：解析为 `PermissionContext`。
- 支持直接返回上下文对象。
- 支持 `{ "data": { ... } }` 包装返回。
- 非 `200`：默认拒绝。
- 空响应体：默认拒绝。
- 请求异常、超时、中断：默认拒绝。

### 5. 上下文缓存与管理

已完成类：

- `PermissionContextCache`
- `CaffeinePermissionContextCache`
- `PermissionContextManager`
- `DefaultPermissionContextManager`
- `PermissionContextHolder`

已完成能力：

- Caffeine 本地缓存。
- 缓存 key：`clientApp:tenantId:userId`。
- 缓存命中不回源权限服务。
- 缓存未命中调用权限服务。
- 权限服务返回 `deny_all=true` 时默认拒绝。
- 上下文 `expires_at` 已过期时默认拒绝。
- 请求结束后清理 `ThreadLocal`。

### 6. Web 接入

已完成类：

- `DataPermissionWebInterceptor`
- `DataPermissionWebMvcConfigurer`

已完成能力：

- Spring MVC 环境下自动注册拦截器。
- `data-permission.web.enabled=false` 时关闭 Web 拦截器。
- 支持配置拦截路径和排除路径。
- 请求进入时加载权限上下文。
- 请求结束后清理 `PermissionContextHolder`。

## 当前接口契约

权限上下文接口由 SDK 调用，业务服务不直接调用。

### 请求

```http
POST /data-auth-center/data-permissions/context
Content-Type: application/json
```

```json
{
  "tenant_id": "t001",
  "user_id": "u001",
  "dept_id": "d001",
  "role_id": "r001"
}
```

### 响应

```json
{
  "asset_group_scope": ["ag001", "ag002"],
  "data_level_scope": ["public", "internal"],
  "deny_all": false,
  "dept_scope": ["d001", "d002"],
  "expires_at": "2026-07-01T11:20:48.762675",
  "export_policies": [
    {
      "allowed": true,
      "is_allowed": true,
      "max_rows": 5000,
      "need_approval": false,
      "resource_id": "security_log",
      "resource_type": "doris"
    }
  ],
  "field_policies": [
    {
      "field_name": "phone",
      "mask_regex": "PHONE",
      "permission_type": "MASK",
      "scene": ["DETAIL"]
    },
    {
      "field_name": "raw_log",
      "mask_regex": null,
      "permission_type": "HIDE",
      "scene": ["DETAIL", "EXPORT"]
    }
  ],
  "resource_object": {
    "doris": ["security_log", "alert_event"],
    "postgre_sql": ["asset", "report"]
  },
  "tenant_id": "ddd111",
  "user_id": "dfew123",
  "version": 12
}
```

## 已完成测试

### 自动装配测试

文件：`DataPermissionAutoConfigurationTests`

覆盖：

- SDK 关闭时不注册核心 Bean。
- SDK 开启时注册核心 Bean。
- SDK 开启时默认注册 `HttpPermissionServiceClient`。
- Web 子开关关闭时不注册 Web Bean。
- 配置绑定。
- 缺少 `service-url` 启动失败。
- SDK 内置 HTTP Client 是主客户端。

### 身份提取测试

文件：`HeaderPermissionIdentityExtractorTests`

覆盖：

- 从 Header 提取完整身份。
- 缺少 `tenant_id` 拒绝。
- 缺少 `user_id` 拒绝。
- 缺少 `dept_id` 拒绝。
- 缺少 `role_id` 拒绝。

### 权限上下文管理测试

文件：`DefaultPermissionContextManagerTests`

覆盖：

- 首次请求回源权限服务，后续命中缓存。
- 权限服务返回 `deny_all=true` 拒绝。
- 权限上下文过期拒绝。

### ThreadLocal 测试

文件：`PermissionContextHolderTests`

覆盖：

- 设置、获取、清理上下文。

### JSON 契约测试

文件：`PermissionContextJsonTests`

覆盖：

- snake_case 字段映射。
- 字段策略解析。
- 导出策略解析。
- `LocalDateTime` 解析。
- 真实权限服务返回样例解析。
- `allowed` / `is_allowed` 兼容。

### HTTP Client 测试

文件：`HttpPermissionServiceClientTests`

覆盖：

- SDK 发起 `POST`。
- URL 使用 `/data-auth-center/data-permissions/context`。
- 请求体只包含 `tenant_id`、`user_id`、`dept_id`、`role_id`。
- 请求体不包含 `client_app`。
- 解析直接上下文响应。
- 解析 `{data: ...}` 包装响应。
- 权限服务返回非 `200` 时拒绝。
- 请求必填字段缺失时拒绝，且不发 HTTP 请求。

### Web 拦截器测试

文件：`DataPermissionWebInterceptorTests`

覆盖：

- 请求进入时绑定权限上下文。
- 请求结束后清理 `ThreadLocal`。

## 待办项

### P0：接入真实业务服务联调

目标：

- 在业务服务中引入 starter。
- 配置真实权限服务 URL。
- 通过真实 HTTP 请求验证 SDK 能拿到权限上下文。

建议验证：

- Header 带齐 `X-Tenant-Id`、`X-User-Id`、`X-Dept-Id`、`X-Role-Id`。
- SDK 发出的请求体符合权限服务契约。
- 权限服务真实返回能被 SDK 解析。
- `PermissionContextHolder.get()` 能在业务 Controller 或 Service 中拿到上下文。
- 缺少任一必填 Header 时返回拒绝。

### P0：异常语义细化

当前实现：

- 非 `200` 统一拒绝。
- HTTP 异常统一拒绝。

后续需要细化：

- `401` / `403`：拒绝。
- `404`：按无权限处理。
- `409`：可考虑重试一次。
- `429`：有可用缓存则使用缓存，无缓存拒绝。
- `500` / `503`：有可用缓存则使用缓存，无缓存拒绝。

涉及模块：

- `HttpPermissionServiceClient`
- `DefaultPermissionContextManager`
- `PermissionContextCache`

### P0：缓存降级策略

当前实现：

- 缓存未命中时调用权限服务。
- 权限服务失败时直接拒绝。

后续目标：

- 权限服务异常时，如果存在未过期缓存，允许使用缓存。
- 权限服务异常且无缓存时拒绝。
- 区分本地缓存 TTL 和权限上下文业务过期时间 `expires_at`。

### P1：SQL 权限过滤

目标：

- 基于 `PermissionContext` 对 SQL 做数据范围过滤。
- 支持 Doris、PostgreSQL 资源映射。
- 支持按部门、资产组、数据等级拼接过滤条件。

初步任务：

- 设计 `ResourceMapping`。
- 从 `data-permission.sql.resources` 加载表名到资源 ID 的映射。
- 使用 JSQLParser 做 SQL AST 改写。
- 对不支持的 SQL 按 `fail-on-unsupported-sql` 决定拒绝或跳过。

### P1：字段权限与脱敏

目标：

- 根据 `field_policies` 控制字段展示。

初步任务：

- `SHOW`：原样返回。
- `MASK`：按 `mask_regex` 脱敏。
- `HIDE`：隐藏字段或置空。
- `DENY`：拒绝访问。
- 支持 `scene`：`ALL`、`QUERY`、`DETAIL`、`EXPORT`。

### P1：导出控制

目标：

- 根据 `export_policies` 控制导出行为。

初步任务：

- 判断资源是否允许导出。
- 校验 `max_rows`。
- `need_approval=true` 时一期先拒绝或返回明确异常。
- 导出场景强制字段脱敏。

### P2：Kafka 缓存失效

目标：

- 权限变更后，权限服务发送失效事件。
- SDK 消费事件并清理本地缓存。

初步任务：

- 定义失效事件模型。
- 监听 `auth-context-invalidate`。
- 支持按用户清理、按租户清理、全量清理。

### P2：审计事件

目标：

- SDK 记录权限拒绝、导出、敏感字段访问等审计事件。

初步任务：

- 定义审计事件模型。
- 异步发送到 Kafka。
- 避免日志或审计中输出敏感字段明文。

### P2：文档与示例工程

目标：

- 提供一个最小业务服务接入示例。

初步任务：

- 示例 `application.yaml`。
- 示例 Controller。
- 示例 Header 调用。
- 示例如何读取 `PermissionContextHolder`。
- 常见错误排查：缺 Header、权限服务超时、上下文过期、IDEA JUnit 版本问题。

## 当前注意事项

- 业务服务不负责调用权限服务，SDK 内置 HTTP Client 负责调用。
- `data-permission.service-url` 当前应配置为完整接口地址：

```yaml
data-permission:
  service-url: http://localhost:8081/data-auth-center/data-permissions/context
```

- `client-app` 目前只用于 SDK 本地缓存 key，不进入权限服务请求体。
- `expires_at` 在 SDK 内部使用 `LocalDateTime`。
- 当前阶段还没有实现 SQL 改写、字段脱敏、导出控制、Kafka 失效、审计上报。

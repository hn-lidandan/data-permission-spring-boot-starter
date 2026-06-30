# Data Permission SDK 开发步骤与验证清单

## 1. 文档目的

本文档用于把 `docs/SDk开发.md` 中的 SDK 设计拆成可执行的开发步骤，并明确每一步如何验证是否成功。

一期目标不是一次性做完整权限平台，而是先完成 Java Spring Boot Starter 形态的权限执行闭环：

- 业务服务引入 SDK 后可以自动装配。
- 请求进入时可以提取身份并绑定权限上下文。
- SQL 执行前可以对受控单表查询追加权限过滤。
- 接口返回时可以按字段策略脱敏。
- 导出前可以做权限和条数校验。
- 权限变更可以清理本地缓存。
- 查询、拒绝、导出行为可以异步上报审计。

## 2. 推荐开发顺序总览

| 阶段 | 开发内容 | 核心验收点 |
| --- | --- | --- |
| 0 | 明确一期边界 | 只支持单表 `SELECT` 和简单单表 `COUNT`，不支持复杂 SQL |
| 1 | Starter 工程骨架 | 业务服务引入依赖后能自动注册 SDK Bean |
| 2 | 配置属性 | `data-permission.*` 配置能正确绑定并支持开关 |
| 3 | 核心模型和异常 | 权限上下文、资源、字段、导出模型稳定 |
| 4 | 请求身份提取 | 能从可信来源提取 `tenant_id`、`user_id` |
| 5 | Context Holder | 一次请求内可读取上下文，请求结束后清理 |
| 6 | Context Manager + Caffeine | 缓存命中不回源，缓存未命中调用权限服务 |
| 7 | 权限服务 Client | 能调用 `/api/v1/permission/context/query` 并处理异常 |
| 8 | 资源映射 | 物理表名能映射到 `resource_type` 和 `resource_id` |
| 9 | SQL 改写引擎 | 单表查询能追加租户、部门、资产组、数据等级过滤 |
| 10 | MyBatis/JDBC 拦截器 | 业务 Mapper 查询前自动触发 SQL 改写 |
| 11 | 字段脱敏 | DTO 返回时根据字段策略脱敏、隐藏或拒绝 |
| 12 | 导出控制 | 导出接口能按策略拒绝或放行 |
| 13 | 审计上报 | 查询、拒绝、导出能生成审计事件 |
| 14 | Kafka 缓存失效 | 收到失效事件后能清理本地上下文缓存 |
| 15 | 示例业务服务集成 | 用一个 demo 服务跑通完整链路 |
| 16 | 发布前检查 | 依赖、日志、文档、兼容性满足 SDK 要求 |

## 3. 阶段 0：明确一期边界

### 要做什么

- 固化一期只做 Java Spring Boot Starter。
- 固化一期只使用 Caffeine 本地缓存。
- 固化一期 SQL 改写只支持：
  - 单表 `SELECT`
  - 简单单表 `COUNT`
  - 有 `WHERE` 时追加 `AND`
  - 无 `WHERE` 时新增 `WHERE`
- 明确以下 SQL 一期默认拒绝：
  - `JOIN`
  - `UNION`
  - 子查询
  - CTE / `WITH`
  - 多表聚合
  - `INSERT`、`UPDATE`、`DELETE`

### 如何验证成功

- `docs/SDk开发.md` 和本文档的一期边界一致。
- 不支持场景在测试用例中都有覆盖。
- 命中受控资源但 SQL 不支持时，测试结果必须是拒绝，而不是放行原 SQL。

## 4. 阶段 1：Starter 工程骨架

### 要做什么

- 将当前工程调整为真正的 Starter 结构。
- 不依赖业务应用的 `main` 方法运行。
- 提供自动装配类，例如：
  - `DataPermissionAutoConfiguration`
  - `DataPermissionWebAutoConfiguration`
  - `DataPermissionMybatisAutoConfiguration`
  - `DataPermissionJacksonAutoConfiguration`
- 提供 Spring Boot 自动装配入口：
  - Spring Boot 3 使用 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
  - 如需兼容 Spring Boot 2，再考虑 `META-INF/spring.factories`
- 所有自动装配都受 `data-permission.enabled=true` 控制。

### 如何验证成功

- 新建或使用测试用例通过 `ApplicationContextRunner` 启动 Spring 上下文。
- 当配置为 `data-permission.enabled=true` 时，核心 Bean 能被加载。
- 当配置为 `data-permission.enabled=false` 时，核心 Bean 不加载。
- 推荐验证命令：

```bash
./mvnw test
```

### 成功标准

- 测试上下文启动成功。
- 不需要启动真实 Web 服务，也能验证自动装配。
- SDK 不强制要求宿主项目必须启动你的 `DataPermissionSpringBootStarterApplication`。

## 5. 阶段 2：配置属性

### 要做什么

- 定义 `DataPermissionProperties`，绑定 `data-permission.*`。
- 至少包含以下配置：
  - `enabled`
  - `service-url`
  - `cache.expire-seconds`
  - `cache.maximum-size`
  - `kafka.enabled`
  - `kafka.invalidate-topic`
  - `kafka.audit-topic`
  - `intercept.fail-on-unsupported-sql`
  - `intercept.resources`
- 对关键配置做校验：
  - 开启 SDK 时 `service-url` 不能为空。
  - 缓存过期时间必须大于 0。
  - 缓存最大容量必须大于 0。
  - 受控资源必须配置 `resource-type` 和 `table-names`。

### 如何验证成功

- 使用单元测试加载 YAML 或 properties。
- 验证配置能正确绑定为 Java 对象。
- 验证非法配置会启动失败或抛出清晰异常。

### 推荐测试点

| 测试场景 | 期望结果 |
| --- | --- |
| `enabled=false` 且无 `service-url` | 启动成功 |
| `enabled=true` 且无 `service-url` | 启动失败 |
| `cache.expire-seconds=60` | 属性值绑定为 60 |
| 配置两个受控资源 | 能读取到两个 resource 配置 |
| 表名大小写不同 | 内部归一化或按设计明确处理 |

## 6. 阶段 3：核心模型和异常

### 要做什么

- 定义 SDK 内部模型：
  - `PermissionContext`
  - `PermissionIdentity`
  - `PermissionResource`
  - `FieldPolicy`
  - `ExportPolicy`
  - `PermissionScope`
  - `AuditEvent`
- 定义枚举：
  - `ResourceType`：`DORIS`、`POSTGRESQL`
  - `PermissionType`：`SHOW`、`MASK`、`HIDE`、`DENY`
  - `AuditAction`：`QUERY`、`DETAIL`、`EXPORT`、`DENIED`
  - `AuditResult`：`ALLOWED`、`DENIED`
- 定义异常：
  - `PermissionContextMissingException`
  - `PermissionDeniedException`
  - `UnsupportedPermissionSqlException`
  - `ExportPermissionDeniedException`
  - `ExportRowsExceededException`

### 如何验证成功

- 权限服务返回的 JSON 示例能反序列化为 `PermissionContext`。
- `resource_type` 小写或历史值能归一化为大写标准值。
- `deny_all=true` 能被正确识别。
- 空 scope 和通配符 `["*"]` 的语义有单元测试覆盖。

### 推荐测试点

| 输入 | 期望结果 |
| --- | --- |
| `deny_all=true` | 当前上下文判定为拒绝 |
| `asset_group_scope=["*"]` | 不生成资产组 `IN` 条件 |
| `data_level_scope=[]` | 数据等级无权限 |
| `resource_object` 不包含当前资源 | 当前资源拒绝 |

## 7. 阶段 4：请求身份提取

### 要做什么

- 定义 `PermissionIdentityExtractor` 接口。
- 提供默认实现，从服务端可信上下文提取身份。
- 一期可以先支持从请求 Header 或 Request Attribute 获取，但必须在文档中说明这些 Header 必须由网关或认证服务写入，不能直接信任前端。
- 必需字段：
  - `tenant_id`
  - `user_id`
- 可选字段：
  - `dept_id`
  - `role_id`
  - `trace_id`
  - `client_ip`
  - `client_app`

### 如何验证成功

- 构造 Mock HTTP 请求，包含完整身份信息时能提取成功。
- 缺少 `tenant_id` 时拒绝。
- 缺少 `user_id` 时拒绝。
- 身份提取失败时不会继续进入 SQL 改写链路。

### 推荐测试点

| 请求数据 | 期望结果 |
| --- | --- |
| 有 `tenant_id`、`user_id` | 返回 `PermissionIdentity` |
| 缺少 `tenant_id` | 抛出权限异常 |
| 缺少 `user_id` | 抛出权限异常 |
| 有 `trace_id` | 审计事件能带上 trace |

## 8. 阶段 5：Context Holder

### 要做什么

- 使用 `ThreadLocal` 实现 `PermissionContextHolder`。
- 提供方法：
  - `set(context)`
  - `get()`
  - `require()`
  - `clear()`
- Web 拦截器在请求进入时设置上下文。
- 请求完成后必须清理上下文。

### 如何验证成功

- 同一线程内 `set` 后可以 `get` 到上下文。
- 调用 `clear` 后 `get` 为空。
- Web 请求完成后一定执行 `clear`。
- 连续两次 Mock 请求不会读到上一次请求的上下文。

### 推荐测试点

| 测试场景 | 期望结果 |
| --- | --- |
| 请求 A 设置用户 A 上下文 | 请求内读取到用户 A |
| 请求 A 结束 | `ThreadLocal` 被清理 |
| 请求 B 设置用户 B 上下文 | 不会读到用户 A |
| 未设置上下文直接 `require()` | 抛出 `PermissionContextMissingException` |

## 9. 阶段 6：Context Manager + Caffeine

### 要做什么

- 实现 `PermissionContextManager`。
- 根据 `tenant_id:user_id:client_app` 生成缓存 Key。
- 缓存命中时直接返回。
- 缓存未命中时调用权限服务 Client。
- 返回 `deny_all=true` 时不应放行。
- `expires_at` 早于当前时间时视为无效。
- 提供按 Key 清理和按用户清理缓存的方法。

### 如何验证成功

- 第一次获取上下文时调用权限服务。
- 第二次获取同一用户上下文时命中 Caffeine，不再调用权限服务。
- 缓存过期后会重新调用权限服务。
- `expires_at` 已过期时不使用该上下文。
- 权限服务异常时，有未过期缓存则使用缓存，无缓存则拒绝。

### 推荐测试点

| 场景 | 期望结果 |
| --- | --- |
| 缓存未命中 | 调用权限服务并写入缓存 |
| 缓存命中 | 不调用权限服务 |
| 权限服务 401/403 | 拒绝 |
| 权限服务 404 | 按无权限处理 |
| 权限服务 429 且有缓存 | 使用未过期缓存 |
| 权限服务 429 且无缓存 | 拒绝 |
| 权限服务 500/503 且有缓存 | 使用未过期缓存 |
| 权限服务 500/503 且无缓存 | 拒绝 |

## 10. 阶段 7：权限服务 Client

### 要做什么

- 实现调用权限服务的 Client。
- 一期接口：
  - `POST /api/v1/permission/context/query`
- 请求参数至少包含：
  - `tenant_id`
  - `user_id`
  - `client_app`
- 响应转换为 `PermissionContext`。
- HTTP 状态码按设计转换为 SDK 行为。

### 如何验证成功

- 使用 MockWebServer 或 WireMock 模拟权限服务。
- 200 响应能正确解析上下文。
- 401、403、404、409、429、500、503 都有测试覆盖。
- 超时配置生效。
- 日志中不能打印敏感字段明文。

### 推荐测试点

| 权限服务响应 | SDK 期望行为 |
| --- | --- |
| 200 + 正常上下文 | 返回上下文 |
| 200 + `deny_all=true` | 拒绝 |
| 401/403 | 拒绝 |
| 404 | 按无权限处理 |
| 409 | 重新拉取一次 |
| 429 | 有缓存用缓存，无缓存拒绝 |
| 500/503 | 有缓存用缓存，无缓存拒绝 |

## 11. 阶段 8：资源映射

### 要做什么

- 从配置中加载资源映射。
- 支持从物理表名找到：
  - `resource_id`
  - `resource_type`
  - 过滤字段映射
- 示例：

```yaml
data-permission:
  intercept:
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
```

### 如何验证成功

- 输入 `normalized_security_log` 能识别为 `security_log` / `DORIS`。
- 输入 `t_alert_event` 能识别为 `alert_event` / `POSTGRESQL`。
- 未配置表默认不做 SQL 改写。
- 已配置表但用户上下文没有该资源权限时拒绝。

### 推荐测试点

| 表名 | 配置状态 | 期望结果 |
| --- | --- | --- |
| `normalized_security_log` | 已配置 | 识别为受控资源 |
| `NORMALIZED_SECURITY_LOG` | 已配置 | 按设计支持或明确不支持大小写 |
| `unknown_table` | 未配置 | 不纳入 SDK 改写 |
| `t_alert_event` | 已配置但上下文无资源 | 拒绝 |

## 12. 阶段 9：SQL 改写引擎

### 要做什么

- 使用 JSqlParser 解析 SQL。
- 只处理一期支持范围内的 SQL。
- 根据权限上下文追加过滤条件：
  - `tenant_id = ?`
  - `dept_id IN (?, ?)`
  - `asset_group_id IN (?, ?)`
  - `data_level IN (?, ?)`
- 原 SQL 参数必须保留在前，权限参数追加在后。
- 通配符 `["*"]` 不生成对应 `IN` 条件。
- 空权限范围生成无结果条件或抛出拒绝异常。
- 不支持 SQL 默认拒绝。

### 如何验证成功

- 对 SQL 改写引擎做纯单元测试，不依赖数据库。
- 校验改写后的 SQL 结构。
- 校验追加参数顺序。
- 校验不支持 SQL 会抛出 `UnsupportedPermissionSqlException`。

### 推荐测试点

| 原 SQL | 期望结果 |
| --- | --- |
| `SELECT * FROM normalized_security_log` | 新增 `WHERE tenant_id = ? ...` |
| `SELECT * FROM normalized_security_log WHERE event_type = ?` | 在原 `WHERE` 后追加 `AND` |
| `SELECT count(*) FROM normalized_security_log` | 支持简单 count 改写 |
| `SELECT * FROM a JOIN b ON ...` | 拒绝 |
| `SELECT * FROM a UNION SELECT * FROM a` | 拒绝 |
| `WITH t AS (...) SELECT * FROM t` | 拒绝 |
| `SELECT * FROM normalized_security_log WHERE tenant_id = ?` | SDK 仍追加自己的 `tenant_id = ?` |
| `asset_group_scope=[]` | 拒绝或生成 `WHERE 1 = 0` |
| `asset_group_scope=["*"]` | 不追加资产组条件 |

### 参数顺序验证

原 SQL：

```sql
SELECT * FROM normalized_security_log WHERE event_type = ?
```

原参数：

```text
["login_failed"]
```

权限上下文：

```text
tenant_id=t001
asset_group_scope=["ag001", "ag002"]
data_level_scope=["public", "internal"]
```

期望最终参数顺序：

```text
["login_failed", "t001", "ag001", "ag002", "public", "internal"]
```

## 13. 阶段 10：MyBatis/JDBC 拦截器

### 要做什么

- 接入 MyBatis 或 MyBatis-Plus 拦截器。
- 在 SQL 执行前拿到：
  - 原始 SQL
  - 原始参数
  - 当前 `PermissionContext`
- 调用 SQL 改写引擎。
- 将改写后的 SQL 和参数交回 MyBatis 执行。
- 记录查询审计摘要。

### 如何验证成功

- 使用测试 Mapper 执行单表查询。
- 日志或测试断言中能看到 SQL 已追加权限条件。
- 当前线程没有上下文时，受控资源查询必须拒绝。
- 未配置资源表查询不被改写。

### 推荐测试点

| 场景 | 期望结果 |
| --- | --- |
| 有上下文 + 查询受控表 | SQL 被改写 |
| 无上下文 + 查询受控表 | 拒绝 |
| 有上下文 + 查询未受控表 | 不改写 |
| 受控表 + JOIN | 拒绝 |
| 改写成功 | 生成 `QUERY` 审计事件 |
| 改写拒绝 | 生成 `DENIED` 审计事件 |

## 14. 阶段 11：字段脱敏

### 要做什么

- 定义注解：
  - `@PermissionResource`
  - `@PermissionField`
- 提供资源场景上下文，例如：
  - `resource_type`
  - `resource_id`
  - `scene`
- 集成 Jackson，在序列化阶段处理字段策略。
- 支持动作：
  - `SHOW`：原样返回
  - `MASK`：脱敏后返回
  - `HIDE`：返回 `null` 或不序列化，一期建议返回 `null`
  - `DENY`：抛出权限异常
- 内置脱敏规则：
  - `PHONE`
  - `EMAIL`
  - `ID_CARD`
  - `IP`
  - `TOKEN`
  - `REGEX`

### 如何验证成功

- DTO 字段有 `@PermissionField` 时按注解字段名匹配策略。
- DTO 字段没有注解时按 Jackson 输出字段名兜底。
- 同名字段在不同资源下可以有不同策略。
- 导出场景 `force_mask=true` 时强制脱敏。

### 推荐测试点

| 字段策略 | 输入值 | 期望输出 |
| --- | --- | --- |
| `SHOW` | `13812345678` | `13812345678` |
| `MASK PHONE` | `13812345678` | 手机号脱敏结果 |
| `MASK EMAIL` | `test@example.com` | 邮箱脱敏结果 |
| `HIDE` | `raw log content` | `null` |
| `DENY` | 任意值 | 抛出权限异常 |
| 未配置字段策略 | 敏感字段 | 按默认策略处理，建议拒绝或隐藏 |

## 15. 阶段 12：导出控制

### 要做什么

- 定义 `@ExportGuard` 注解。
- 定义 `DataPermissionExport.checkRows(count)`。
- AOP 拦截导出方法。
- 校验：
  - 是否有当前上下文
  - 是否有资源权限
  - 是否存在导出策略
  - `is_allowed`
  - `max_rows`
  - `need_approval`
  - `force_mask`
- 一期 `need_approval=true` 直接拒绝。

### 如何验证成功

- 未配置导出策略时拒绝。
- `is_allowed=false` 时拒绝。
- `max_rows=0` 时拒绝。
- 预计导出条数超过 `max_rows` 时拒绝。
- `need_approval=true` 时拒绝。
- 放行导出时生成 `EXPORT` 审计。
- 拒绝导出时生成 `DENIED` 或失败 `EXPORT` 审计。

### 推荐测试点

| 策略 | 预计条数 | 期望结果 |
| --- | --- | --- |
| 无策略 | 100 | 拒绝 |
| `is_allowed=false` | 100 | 拒绝 |
| `max_rows=0` | 100 | 拒绝 |
| `max_rows=5000` | 6000 | 拒绝 |
| `need_approval=true` | 100 | 拒绝 |
| `is_allowed=true,max_rows=5000` | 100 | 放行 |

## 16. 阶段 13：审计上报

### 要做什么

- 定义 `AuditReporter` 接口。
- 一期实现 Kafka 异步上报。
- 审计事件至少包含：
  - `tenant_id`
  - `user_id`
  - `action`
  - `resource_type`
  - `resource_id`
  - `filter_sql`
  - `result`
  - `trace_id`
  - `client_ip`
  - `created_at`
- 审计发送失败不能影响主业务请求。
- 审计日志不能包含敏感字段明文。

### 如何验证成功

- SQL 改写成功时生成 `QUERY` 审计。
- 字段拒绝时生成 `DENIED` 审计。
- 导出成功时生成 `EXPORT` 审计。
- 导出失败时生成拒绝审计。
- Kafka 发送失败时，业务请求不失败，只记录 SDK 错误日志。

### 推荐测试点

| 场景 | 期望审计 |
| --- | --- |
| 查询成功 | `action=QUERY,result=ALLOWED` |
| 无资源权限 | `action=DENIED,result=DENIED` |
| 不支持 SQL | `action=DENIED,result=DENIED` |
| 导出成功 | `action=EXPORT,result=ALLOWED` |
| 导出超限 | `action=EXPORT,result=DENIED` 或 `action=DENIED,result=DENIED` |
| Kafka 发送异常 | 主流程不受影响 |

## 17. 阶段 14：Kafka 缓存失效

### 要做什么

- 定义权限失效事件模型。
- 监听权限服务发送的失效 Topic。
- 支持清理：
  - 单个用户上下文
  - 单个租户下所有上下文
  - 全量上下文
- 消息字段建议包含：
  - `tenant_id`
  - `user_id`
  - `client_app`
  - `version`
  - `event_time`

### 如何验证成功

- 缓存中已有用户上下文。
- 收到该用户失效事件后，缓存被清理。
- 下一次请求会重新调用权限服务。
- Kafka 消费失败时不影响业务线程。
- Kafka 未启用时，SDK 仍依赖 TTL 最终过期。

### 推荐测试点

| 失效事件 | 期望结果 |
| --- | --- |
| 指定 `tenant_id + user_id + client_app` | 清理单个缓存 Key |
| 只指定 `tenant_id` | 清理该租户缓存 |
| 全量失效事件 | 清空全部缓存 |
| Kafka 关闭 | 不启动消费者 |
| 消息格式错误 | 记录日志并跳过 |

## 18. 阶段 15：示例业务服务集成

### 要做什么

- 建议在测试目录或单独 demo 模块准备一个最小业务服务。
- demo 包含：
  - 一个受控表查询 Mapper
  - 一个未受控表查询 Mapper
  - 一个详情 DTO
  - 一个导出方法
  - 一组测试配置
- 使用 Mock 权限服务返回固定权限上下文。

### 如何验证成功

- 启动 demo 上下文成功。
- 访问带身份的查询接口，SQL 自动追加权限条件。
- 缺少身份时返回 403 或抛出权限异常。
- 查询详情时敏感字段被脱敏。
- 导出超限时被拒绝。
- 审计事件被生成。

### 最小集成验收用例

| 用例 | 期望结果 |
| --- | --- |
| 用户有 `security_log` 权限，查询日志 | 返回过滤后的结果 |
| 用户无 `security_log` 权限，查询日志 | 拒绝 |
| 权限服务不可用且无缓存 | 拒绝 |
| 权限服务不可用但有未过期缓存 | 使用缓存继续 |
| 日志详情包含手机号 | 按策略脱敏 |
| 导出 6000 条，最大允许 5000 条 | 拒绝 |

## 19. 阶段 16：发布前检查

### 要做什么

- 检查 SDK 不应强制引入业务方不需要的组件。
- Kafka、MyBatis、Web、Jackson 相关能力应尽量条件装配。
- 检查依赖版本，不要混用明显不兼容版本。
- 检查日志：
  - 不打印 token
  - 不打印敏感字段明文
  - 拒绝原因清晰
- 检查文档：
  - 如何引入依赖
  - 如何配置资源映射
  - 如何声明字段策略
  - 如何声明导出方法
  - 常见异常说明
- 检查测试覆盖：
  - 默认拒绝
  - ThreadLocal 清理
  - 缓存命中和失效
  - SQL 改写
  - 不支持 SQL 拒绝
  - 字段脱敏
  - 导出控制
  - 审计失败不阻塞

### 如何验证成功

- 执行完整测试：

```bash
./mvnw test
```

- 打包成功：

```bash
./mvnw clean package
```

- 在 demo 服务中以普通依赖方式引入 SDK，不需要复制 SDK 源码。
- 关闭 `data-permission.enabled=false` 后，业务服务能正常启动且 SDK 不生效。
- 开启 `data-permission.enabled=true` 后，SDK 自动装配并执行权限控制。

## 20. 一期最终验收标准

一期完成后，至少满足以下验收标准：

| 能力 | 验收标准 |
| --- | --- |
| 自动装配 | 引入 Starter 后通过配置开启即可生效 |
| 身份提取 | 缺少 `tenant_id` 或 `user_id` 默认拒绝 |
| 上下文缓存 | Caffeine 命中不回源，失效后重新拉取 |
| 默认拒绝 | 上下文不可用、资源无权限、不支持 SQL 均拒绝 |
| SQL 改写 | 单表 `SELECT` 和简单 `COUNT` 能追加权限条件 |
| 参数安全 | 权限值全部通过参数化方式追加 |
| 字段脱敏 | 支持 `SHOW`、`MASK`、`HIDE`、`DENY` |
| 导出控制 | 支持导出开关、最大条数、强制脱敏 |
| 审计 | 查询、拒绝、导出能生成审计事件 |
| 缓存失效 | 收到 Kafka 失效事件后能清理本地缓存 |
| 低侵入 | 业务服务不需要手工拼接权限 SQL |

## 21. 建议先做的最小闭环

如果需要最快看到 SDK 价值，建议先按以下最小闭环开发：

1. Starter 自动装配。
2. 配置属性绑定。
3. 请求身份提取。
4. `ThreadLocal` 上下文绑定和清理。
5. Mock 权限服务 Client。
6. Caffeine 缓存。
7. 单表 SQL 改写引擎。
8. MyBatis 拦截器。
9. 默认拒绝异常。
10. 最小 demo 查询验证。

这个闭环跑通后，再继续做字段脱敏、导出控制、Kafka 失效和审计。这样风险最低，也最容易定位问题。

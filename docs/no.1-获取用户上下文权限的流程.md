# 获取用户上下文权限的完整流程

本文档说明当前 SDK 在业务服务中获取用户上下文权限的完整链路。

当前实现的核心能力是：HTTP 请求进入业务服务时，SDK 自动提取用户身份，查询或回源获取权限上下文，写入本地缓存，并把 `PermissionContext` 绑定到当前请求线程。

> 注意：当前阶段已经完成上下文获取与绑定，但还没有实现 MyBatis/JDBC SQL 自动改写。因此业务代码如果要做数据过滤，需要先从 `PermissionContextHolder` 读取权限范围，再在查询条件中使用。

## 1. 前置条件

业务服务需要引入当前 starter，并显式开启 SDK。

```yaml
data-permission:
  enabled: true
  service-url: http://localhost:8081/data-auth-center/data-permissions/context
  client-app: your-service-name

  cache:
    expire-seconds: 60
    maximum-size: 10000

  web:
    enabled: true
    path-pattern: /**
    exclude-path-patterns:
      - /actuator/**
      - /health
```

关键配置说明：

| 配置项 | 说明 |
| --- | --- |
| `data-permission.enabled` | SDK 总开关，默认为 `false` |
| `data-permission.service-url` | 权限服务上下文接口完整地址，开启 SDK 后必填 |
| `data-permission.client-app` | 当前业务服务标识，参与缓存 Key |
| `data-permission.cache.expire-seconds` | 本地权限上下文缓存过期时间 |
| `data-permission.cache.maximum-size` | 本地缓存最大容量 |
| `data-permission.web.enabled` | Web 拦截器开关，默认开启 |

## 2. 启动阶段：自动装配 SDK Bean

业务服务启动时，Spring Boot 会读取：

```text
META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
```

当前注册的自动装配类包括：

```text
org.com.it.permission.autoconfigure.DataPermissionPropertiesAutoConfiguration
org.com.it.permission.autoconfigure.DataPermissionAutoConfiguration
org.com.it.permission.autoconfigure.DataPermissionWebAutoConfiguration
```

### 2.1 配置属性绑定

`DataPermissionPropertiesAutoConfiguration` 始终生效，负责绑定 `data-permission.*` 配置到 `DataPermissionProperties`。

即使 `data-permission.enabled=false`，配置属性仍然可以绑定，但核心权限 Bean 不会注册。

### 2.2 核心能力装配

当满足下面配置时：

```yaml
data-permission:
  enabled: true
```

`DataPermissionAutoConfiguration` 会注册核心 Bean：

| Bean | 作用 |
| --- | --- |
| `permissionContextCaffeineCache` | Caffeine 原生缓存 |
| `permissionContextCache` | SDK 缓存抽象 |
| `permissionServiceClient` | SDK 内置 HTTP 权限服务客户端 |
| `permissionContextManager` | 权限上下文管理器 |

启动时还会校验：

- `data-permission.service-url` 不能为空。
- `cache.expire-seconds` 必须大于 0。
- `cache.maximum-size` 必须大于 0。
- `client.connect-timeout-millis` 必须大于 0。
- `client.request-timeout-millis` 必须大于 0。

如果 SDK 已开启但 `service-url` 缺失，业务服务会启动失败。

### 2.3 Web 拦截器装配

如果业务服务存在 Spring MVC，并且没有关闭：

```yaml
data-permission:
  web:
    enabled: true
```

`DataPermissionWebAutoConfiguration` 会注册：

| Bean | 作用 |
| --- | --- |
| `PermissionIdentityExtractor` | 提取当前请求身份 |
| `DataPermissionWebInterceptor` | 请求进入时获取权限上下文，请求结束时清理 |
| `DataPermissionWebMvcConfigurer` | 把 SDK 拦截器加入 Spring MVC 拦截链 |

默认拦截路径来自：

```yaml
data-permission:
  web:
    path-pattern: /**
    exclude-path-patterns: []
```

## 3. 请求阶段：入口拦截

HTTP 请求进入业务服务后，在 Controller 执行前，Spring MVC 会调用：

```java
DataPermissionWebInterceptor.preHandle(...)
```

当前 `preHandle` 做三件事：

```java
PermissionIdentity identity = identityExtractor.extract(request);
PermissionContext context = contextManager.getContext(identity);
PermissionContextHolder.set(context);
```

也就是：

1. 提取当前请求身份。
2. 根据身份获取权限上下文。
3. 把权限上下文绑定到当前请求线程。

如果过程中出现 SDK 权限异常，拦截器会：

1. 清理 `PermissionContextHolder`。
2. 返回 HTTP 403。
3. 阻止请求继续进入 Controller。

## 4. 身份提取流程

当前默认身份提取器是：

```text
HeaderPermissionIdentityExtractor
```

它从 HTTP Header 中读取身份信息。

默认必填 Header：

| Header | 对应字段 |
| --- | --- |
| `X-Tenant-Id` | `tenant_id` |
| `X-User-Id` | `user_id` |
| `X-Dept-Id` | `dept_id` |
| `X-Role-Id` | `role_id` |

默认可选 Header：

| Header | 对应字段 |
| --- | --- |
| `X-Trace-Id` | `trace_id` |
| `X-Client-App` | `client_app` |
| `X-Forwarded-For` | `client_ip` |

如果 `X-Client-App` 不存在，会使用配置中的：

```yaml
data-permission:
  client-app: your-service-name
```

如果 `X-Forwarded-For` 存在，SDK 会取第一个 IP 作为客户端 IP。

生产环境注意事项：

- 默认 Header 方式只适合先跑通链路。
- 这些 Header 应由网关或认证组件写入。
- 不应直接信任前端传入的身份 Header。
- 正式接入时，业务服务可以提供自己的 `PermissionIdentityExtractor` Bean，从认证上下文中读取身份。

## 5. 权限上下文获取流程

身份提取成功后，SDK 调用：

```java
PermissionContext context = contextManager.getContext(identity);
```

默认实现是：

```text
DefaultPermissionContextManager
```

完整流程如下。

### 5.1 构造缓存 Key

当前缓存 Key 格式：

```text
clientApp:tenantId:userId:deptId:roleId
```

示例：

```text
log-service:t001:u001:d001:r001
```

`clientApp`、`tenantId`、`userId`、`deptId`、`roleId` 都参与缓存 Key，避免同一个用户在不同业务系统、部门或角色下复用错误上下文。

### 5.2 先查本地缓存

SDK 先查询本地 Caffeine 缓存：

```java
cache.get(key)
```

如果命中，直接返回缓存中的 `PermissionContext`。

### 5.3 缓存未命中时回源权限服务

如果缓存未命中，SDK 调用：

```java
PermissionServiceClient.queryContext(identity)
```

默认客户端是：

```text
HttpPermissionServiceClient
```

它会向 `data-permission.service-url` 配置的地址发送 POST 请求。

请求 Header：

```text
Content-Type: application/json
Accept: application/json
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

当前请求体不会发送 `client_app`、`trace_id`、`client_ip`。

## 6. 权限服务返回格式

SDK 支持权限服务直接返回上下文对象。

```json
{
  "tenant_id": "t001",
  "user_id": "u001",
  "dept_scope": ["d001", "d002"],
  "asset_group_scope": ["ag001", "ag002"],
  "data_level_scope": ["public", "internal"],
  "resource_object": {
    "doris": ["security_log", "alert_event"]
  },
  "field_policies": [],
  "export_policies": [],
  "deny_all": false,
  "version": 12,
  "expires_at": "2026-07-13T18:00:00"
}
```

也支持统一响应包装在 `data` 字段里：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "tenant_id": "t001",
    "user_id": "u001",
    "deny_all": false,
    "expires_at": "2026-07-13T18:00:00"
  }
}
```

`PermissionContext` 当前主要字段：

| 字段 | 说明 |
| --- | --- |
| `tenant_id` | 租户 ID |
| `user_id` | 用户 ID |
| `dept_scope` | 可访问部门范围 |
| `asset_group_scope` | 可访问资产组范围 |
| `data_level_scope` | 可访问数据等级范围 |
| `resource_object` | 可访问资源对象 |
| `field_policies` | 字段策略 |
| `export_policies` | 导出策略 |
| `deny_all` | 是否拒绝全部访问 |
| `version` | 权限上下文版本 |
| `expires_at` | 权限上下文过期时间 |

## 7. 回源结果校验与缓存写入

权限服务返回 HTTP 200 后，SDK 会反序列化为 `PermissionContext`，然后做拒绝态判断。

下面任意情况都会拒绝：

- 权限服务返回非 200。
- 权限服务响应体为空。
- 请求权限服务发生 IO 异常或请求被中断。
- 反序列化失败。
- `PermissionContext` 为 `null`。
- `deny_all=true`。
- `expires_at` 早于当前时间。

校验通过后，SDK 写入本地缓存：

```java
cache.put(key, context);
```

然后返回 `PermissionContext` 给 Web 拦截器。

## 8. 绑定当前请求上下文

Web 拦截器拿到 `PermissionContext` 后执行：

```java
PermissionContextHolder.set(context);
```

`PermissionContextHolder` 内部使用 `ThreadLocal` 保存当前请求线程的权限上下文。

业务代码可以这样读取：

```java
import org.com.it.permission.context.PermissionContextHolder;
import org.com.it.permission.model.PermissionContext;

PermissionContext context = PermissionContextHolder.require();

List<String> deptScope = context.getDeptScope();
List<String> assetGroupScope = context.getAssetGroupScope();
List<String> dataLevelScope = context.getDataLevelScope();
```

如果不确定当前线程是否存在权限上下文，可以使用：

```java
PermissionContextHolder.get().ifPresent(context -> {
    // use context
});
```

如果使用 `require()` 但当前线程没有上下文，会抛出 `PermissionContextMissingException`。

## 9. 请求结束后清理

请求完成后，Spring MVC 会调用：

```java
DataPermissionWebInterceptor.afterCompletion(...)
```

SDK 会执行：

```java
PermissionContextHolder.clear();
```

这样可以避免 Web 容器线程池复用时，后一个请求读到前一个请求的权限上下文。

## 10. 完整时序图

```text
业务请求
  |
  v
Spring MVC Interceptor
  |
  v
DataPermissionWebInterceptor.preHandle
  |
  v
PermissionIdentityExtractor.extract
  |
  v
从 Header 或业务自定义身份源读取 tenant/user/dept/role
  |
  v
DefaultPermissionContextManager.getContext
  |
  v
构造缓存 Key: clientApp:tenantId:userId:deptId:roleId
  |
  +-- 缓存命中 --> 返回 PermissionContext
  |
  +-- 缓存未命中
        |
        v
      HttpPermissionServiceClient.queryContext
        |
        v
      POST data-permission.service-url
        |
        v
      解析 PermissionContext
        |
        v
      校验 deny_all / expires_at
        |
        v
      写入 Caffeine 缓存
        |
        v
      返回 PermissionContext
  |
  v
PermissionContextHolder.set(context)
  |
  v
Controller / Service 读取 PermissionContextHolder
  |
  v
DataPermissionWebInterceptor.afterCompletion
  |
  v
PermissionContextHolder.clear()
```

## 11. 当前实现的边界

### 11.1 已实现

- Spring Boot 3 自动装配。
- `data-permission.enabled` 总开关。
- `data-permission.service-url` 启动期校验。
- 默认 Header 身份提取。
- Spring MVC 请求拦截。
- Caffeine 本地缓存。
- 缓存未命中时调用权限服务。
- 权限上下文反序列化。
- `deny_all` 和 `expires_at` 拒绝态判断。
- `PermissionContextHolder` 当前线程绑定与清理。

### 11.2 暂未实现或需注意

- 暂未实现 SQL 自动改写。
- 暂未实现字段脱敏模块。
- 暂未实现导出 AOP。
- 暂未实现 Kafka 缓存失效和审计上报。
- 缓存命中时当前不会再次检查 `PermissionContext.expires_at`，主要依赖 Caffeine 的 `expire-seconds` 控制缓存生命周期。
- 权限服务异常时，如果缓存未命中，当前策略是默认拒绝，不会降级放行。

## 12. 常见 403 原因

| 现象 | 可能原因 |
| --- | --- |
| 启动失败 | `data-permission.enabled=true` 但没有配置 `service-url` |
| 请求直接 403 | 缺少 `X-Tenant-Id` |
| 请求直接 403 | 缺少 `X-User-Id` |
| 请求直接 403 | 缺少 `X-Dept-Id` |
| 请求直接 403 | 缺少 `X-Role-Id` |
| 请求直接 403 | 权限服务返回非 200 |
| 请求直接 403 | 权限服务返回空响应 |
| 请求直接 403 | 权限服务返回 `deny_all=true` |
| 请求直接 403 | 权限服务返回的 `expires_at` 已过期 |
| 业务代码读取上下文失败 | 当前请求没有经过 SDK Web 拦截器，或已经离开原请求线程 |

## 13. 关键代码位置

| 文件 | 作用 |
| --- | --- |
| `src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` | Spring Boot 自动装配入口 |
| `src/main/java/org/com/it/permission/autoconfigure/DataPermissionPropertiesAutoConfiguration.java` | 配置属性绑定 |
| `src/main/java/org/com/it/permission/autoconfigure/DataPermissionAutoConfiguration.java` | 核心 Bean 自动装配 |
| `src/main/java/org/com/it/permission/autoconfigure/DataPermissionWebAutoConfiguration.java` | Web 拦截器自动装配 |
| `src/main/java/org/com/it/permission/web/DataPermissionWebInterceptor.java` | 请求入口拦截与上下文绑定 |
| `src/main/java/org/com/it/permission/web/DataPermissionWebMvcConfigurer.java` | 注册 MVC 拦截器 |
| `src/main/java/org/com/it/permission/identity/HeaderPermissionIdentityExtractor.java` | 默认 Header 身份提取 |
| `src/main/java/org/com/it/permission/context/DefaultPermissionContextManager.java` | 缓存查询、权限服务回源、拒绝态判断 |
| `src/main/java/org/com/it/permission/service/HttpPermissionServiceClient.java` | 调用权限服务并解析响应 |
| `src/main/java/org/com/it/permission/context/PermissionContextHolder.java` | 当前线程权限上下文持有器 |
| `src/main/java/org/com/it/permission/model/PermissionContext.java` | 权限上下文模型 |

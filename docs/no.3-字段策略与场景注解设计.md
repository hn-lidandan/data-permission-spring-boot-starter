# 字段策略与场景注解设计

## 1. 目标

字段策略用于控制接口返回字段的展示方式。

SQL 行级过滤控制：

```text
当前用户能查询哪些数据行。
```

字段策略控制：

```text
当前用户在指定场景下，某个字段应该明文返回、脱敏、隐藏，还是拒绝访问。
```

当前阶段字段策略基于权限上下文中的 `field_policies` 执行。

示例：

```json
{
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
  ]
}
```

## 2. 核心结论

当前 MVP 规则：

```text
没有 @PermissionScene 的普通列表接口，不处理字段策略。
只有显式声明 @PermissionScene 的接口，才执行字段策略。
字段策略当前按 field_name + scene 匹配。
@PermissionField 用于解决 DTO 字段名和权限字段名不一致的问题。
没有匹配到字段策略时默认原样返回。
```

## 3. 场景注解设计

### 3.1 为什么需要场景

同一个字段在不同接口场景下可能有不同展示要求。

例如：

```json
{
  "field_name": "phone",
  "permission_type": "MASK",
  "scene": ["DETAIL"]
}
```

表示：

```text
只有 DETAIL 场景下，phone 字段需要脱敏。
```

普通列表接口如果没有声明场景，则不处理字段策略。

### 3.2 PermissionScene 注解

建议新增注解：

```java
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface PermissionScene {

    String value();
}
```

说明：

- 支持标在方法上。
- 支持标在类上。
- 方法注解优先于类注解。
- `value` 不设置默认值，业务必须显式声明场景。

### 3.3 场景常量

建议新增常量类：

```java
public final class PermissionScenes {

    public static final String DETAIL = "DETAIL";

    public static final String EXPORT = "EXPORT";

    private PermissionScenes() {
    }
}
```

当前不建议暴露 `QUERY` 给业务使用。

原因：

```text
普通列表接口不写 @PermissionScene，就不执行字段策略。
```

如果后续列表场景也需要字段策略，再补充：

```java
public static final String QUERY = "QUERY";
```

### 3.4 使用示例

详情接口：

```java
@PermissionScene(PermissionScenes.DETAIL)
@GetMapping("/detail")
public LogDetailVO detail() {
    return service.detail();
}
```

导出接口：

```java
@PermissionScene(PermissionScenes.EXPORT)
@GetMapping("/export")
public void export() {
    service.export();
}
```

普通列表接口：

```java
@GetMapping("/list")
public List<LogListVO> list() {
    return service.list();
}
```

列表接口不写注解，因此字段策略不处理。

### 3.5 AOP 行为

建议通过 AOP 自动设置当前场景。

流程：

```text
1. 拦截带 @PermissionScene 的类或方法。
2. 方法注解优先于类注解。
3. 读取注解 value。
4. 保存旧 scene。
5. 设置新 scene 到 DataPermissionSceneHolder。
6. 执行业务方法。
7. finally 中恢复旧 scene；如果旧 scene 不存在，则清理。
```

这样可以避免业务代码手动调用：

```java
DataPermissionSceneHolder.set("DETAIL");
```

也能避免嵌套调用时场景串扰。

## 4. 字段注解设计

### 4.1 为什么需要字段注解

权限上下文中的字段名不一定等于 DTO 字段名。

例如权限策略中是：

```json
{
  "field_name": "phone",
  "permission_type": "MASK",
  "scene": ["DETAIL"]
}
```

但业务 DTO 里可能是：

```java
private String mobile;
```

如果没有字段注解，SDK 只能拿到 `mobile`，无法匹配策略里的 `phone`。

### 4.2 PermissionField 注解

建议新增注解：

```java
@Target({ElementType.FIELD, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface PermissionField {

    String value();
}
```

说明：

- 支持标在字段上。
- 支持标在 getter 方法上。
- `value` 必填，表示权限系统中的字段名。

### 4.3 使用示例

```java
public class LogDetailVO {

    @PermissionField("phone")
    private String mobile;

    @PermissionField("raw_log")
    private String rawLog;

    private String username;
}
```

含义：

```text
mobile 字段按权限字段 phone 匹配策略。
rawLog 字段按权限字段 raw_log 匹配策略。
username 没有注解，按输出字段名或 Java 字段名兜底匹配。
```

### 4.4 字段名匹配优先级

字段策略匹配字段名的优先级：

```text
1. @PermissionField("xxx")
2. Jackson 输出字段名
3. Java 字段名
```

示例：

```java
public class UserVO {

    @PermissionField("phone")
    @JsonProperty("mobile_phone")
    private String mobile;
}
```

匹配权限策略时使用：

```text
phone
```

输出 JSON 字段名仍然由 Jackson 决定，例如：

```json
{
  "mobile_phone": "138****5678"
}
```

## 5. 字段策略匹配规则

当前阶段只按以下维度匹配：

```text
field_name + scene
```

暂不按 `resource_id` 匹配，因为当前 `field_policies` 中没有资源维度。

规则：

```text
没有 @PermissionScene：字段策略不执行。
有 @PermissionScene：读取当前 scene。
当前 scene 在 policy.scene 中：策略生效。
policy.scene 为空：按 ALL 处理。
没有匹配策略：默认 SHOW。
```

## 6. permission_type 行为

当前建议支持：

| permission_type | 行为 |
| --- | --- |
| `SHOW` | 原样返回 |
| `MASK` | 脱敏后返回 |
| `HIDE` | 返回 `null` |
| `DENY` | 抛权限异常，拒绝本次响应 |

### 6.1 MASK

`MASK` 表示字段值需要脱敏。

示例：

```json
{
  "field_name": "phone",
  "mask_regex": "PHONE",
  "permission_type": "MASK",
  "scene": ["DETAIL"]
}
```

处理：

```text
13812345678 -> 138****5678
```

当前已支持的内置脱敏规则：

| mask_regex | 示例输入 | 示例输出 | 说明 |
| --- | --- | --- | --- |
| `PHONE` | `13812345678` | `138****5678` | 手机号保留前三位和后四位 |
| `EMAIL` | `zhangsan@example.com` | `z****@example.com` | 邮箱保留首字母和域名 |
| `ID_CARD` | `430123199901011234` | `430***********1234` | 身份证保留前三位和后四位 |
| `TOKEN` | `sk-abcdefghijklmnopqrstuvwxyz` | `sk-************` | Token 保留 `sk-` 前缀 |
| `ACCESS_KEY` | `AKIA1234567890ABCDEFG` | `AKIA************` | AccessKey 保留 `AKIA` 前缀 |
| 其他或为空 | `abcdef` | `ab****ef` | 默认脱敏，长文本保留前两位和后两位 |

说明：

```text
mask_regex 当前作为“脱敏规则名称”使用，例如 PHONE、EMAIL、ID_CARD。
如果传入未知规则或空规则，会走默认脱敏。
后续如需真正按自定义正则脱敏，再扩展 REGEX/custom_regex 规则。
```

### 6.2 HIDE

`HIDE` 表示隐藏字段值。

当前建议处理为：

```text
字段返回 null。
```

这样前端字段结构稳定，不会因为字段消失导致兼容问题。

### 6.3 DENY

`DENY` 表示禁止访问该字段。

当前建议：

```text
抛权限异常，拒绝本次响应。
```

## 7. 示例流程

### 7.1 DETAIL 场景

接口：

```java
@PermissionScene(PermissionScenes.DETAIL)
@GetMapping("/detail")
public LogDetailVO detail() {
    return service.detail();
}
```

DTO：

```java
public class LogDetailVO {

    @PermissionField("phone")
    private String mobile;

    @PermissionField("raw_log")
    private String rawLog;

    private String username;
}
```

权限上下文：

```json
{
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
  ]
}
```

业务原始返回：

```json
{
  "mobile": "13812345678",
  "rawLog": "原始日志内容",
  "username": "zhangsan"
}
```

字段策略处理后：

```json
{
  "mobile": "138****5678",
  "rawLog": null,
  "username": "zhangsan"
}
```

原因：

```text
mobile 通过 @PermissionField("phone") 匹配 phone，DETAIL 场景下 MASK。
rawLog 通过 @PermissionField("raw_log") 匹配 raw_log，DETAIL 场景下 HIDE。
username 没有匹配策略，默认 SHOW。
```

### 7.2 EXPORT 场景

接口：

```java
@PermissionScene(PermissionScenes.EXPORT)
@GetMapping("/export")
public void export() {
    service.export();
}
```

同样字段策略下：

```text
phone 只配置 DETAIL，因此 EXPORT 不处理。
raw_log 配置 DETAIL 和 EXPORT，因此 EXPORT 下隐藏。
```

### 7.3 普通列表接口

接口：

```java
@GetMapping("/list")
public List<LogListVO> list() {
    return service.list();
}
```

没有 `@PermissionScene`：

```text
字段策略不执行。
```

即使 `field_policies` 中有 `phone` 或 `raw_log` 策略，也不会处理列表返回字段。

## 8. 建议模块划分

```text
scene/
  PermissionScene
  PermissionScenes
  PermissionSceneAspect

masking/
  PermissionField
  FieldPolicyMatcher
  DataMaskingEngine
  MaskRule
  MaskRules
  PermissionJacksonModule
  DataPermissionMaskingAutoConfiguration
```

职责：

| 模块 | 作用 |
| --- | --- |
| `PermissionScene` | 声明当前接口场景 |
| `PermissionScenes` | 提供场景常量 |
| `PermissionSceneAspect` | 通过 AOP 设置和恢复当前场景 |
| `PermissionField` | 声明 DTO 字段对应的权限字段名 |
| `FieldPolicyMatcher` | 根据 `field_name + scene` 匹配字段策略 |
| `DataMaskingEngine` | 执行 `SHOW / MASK / HIDE / DENY` |
| `MaskRules` | 内置脱敏规则 |
| `PermissionJacksonModule` | 接入 Jackson 序列化流程 |

## 9. 业务服务使用步骤

### 9.1 开启配置

业务服务需要先开启 SDK 和字段策略：

```yaml
data-permission:
  enabled: true
  service-url: http://permission-service/api/v1/permission/context
  client-app: log-service

  web:
    enabled: true

  field-mask:
    enabled: true
```

说明：

```text
enabled=true 负责开启整个 SDK。
service-url 是 SDK 获取 PermissionContext 的权限服务接口地址。
client-app 是当前业务服务标识。
web.enabled=true 负责请求进入时获取 PermissionContext。
field-mask.enabled=true 负责注册 Jackson 字段策略模块。
```

如果业务服务还需要同时启用 SQL 行级过滤，可以继续开启：

```yaml
data-permission:
  sql:
    enabled: true
```

完整示例：

```yaml
data-permission:
  enabled: true
  service-url: http://permission-service/api/v1/permission/context
  client-app: log-service

  web:
    enabled: true

  sql:
    enabled: true

  field-mask:
    enabled: true
```

注意：

```text
字段策略规则本身不写在业务服务配置文件里。
字段策略来自权限服务返回的 PermissionContext.field_policies。
业务服务配置文件只负责打开 SDK、Web 拦截器、SQL 过滤、字段策略等模块。
```

如果业务服务要使用 `@PermissionScene`，需要确保 AOP 可用。通常业务服务需要引入：

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-aop</artifactId>
</dependency>
```

### 9.2 确保请求能拿到权限上下文

请求进入业务服务时，SDK Web 拦截器会从 Header 提取身份：

```text
X-Tenant-Id
X-User-Id
X-Dept-Id
X-Role-Id
```

然后调用权限服务获取 `PermissionContext`，并写入：

```java
PermissionContextHolder
```

字段策略后续只从当前线程的 `PermissionContextHolder` 读取上下文，不会再次调用权限服务。

### 9.3 权限服务返回 field_policies

权限上下文中需要包含字段策略：

```json
{
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
  ]
}
```

字段策略的含义：

```text
phone 在 DETAIL 场景下脱敏。
raw_log 在 DETAIL 和 EXPORT 场景下隐藏。
```

### 9.4 在接口上声明场景

普通列表接口不写 `@PermissionScene`，字段策略不会处理：

```java
@GetMapping("/list")
public List<LogListVO> list() {
    return service.list();
}
```

详情接口需要显式声明 `DETAIL`：

```java
@PermissionScene(PermissionScenes.DETAIL)
@GetMapping("/detail")
public LogDetailVO detail() {
    return service.detail();
}
```

导出接口需要显式声明 `EXPORT`：

```java
@PermissionScene(PermissionScenes.EXPORT)
@GetMapping("/export")
public void export() {
    service.export();
}
```

### 9.5 在 DTO 字段上声明权限字段名

如果 DTO 字段名和权限服务里的 `field_name` 不一致，需要加 `@PermissionField`。

示例：

```java
public class LogDetailVO {

    @PermissionField("phone")
    private String mobile;

    @PermissionField("raw_log")
    private String rawLog;

    private String username;
}
```

含义：

```text
mobile 字段按 phone 匹配字段策略。
rawLog 字段按 raw_log 匹配字段策略。
username 没有注解，按输出字段名或 Java 字段名兜底匹配。
```

如果 DTO 字段名和 `field_name` 本来一致，可以不写 `@PermissionField`：

```java
private String phone;
```

### 9.6 预期输出示例

详情接口：

```java
@PermissionScene(PermissionScenes.DETAIL)
```

业务原始返回：

```json
{
  "mobile": "13812345678",
  "rawLog": "原始日志内容",
  "username": "zhangsan"
}
```

字段策略处理后：

```json
{
  "mobile": "138****5678",
  "rawLog": null,
  "username": "zhangsan"
}
```

普通列表接口没有 `@PermissionScene`，则不会处理字段：

```json
{
  "mobile": "13812345678",
  "rawLog": "原始日志内容",
  "username": "zhangsan"
}
```

### 9.7 常见排查点

如果字段没有被处理，优先检查：

```text
1. data-permission.field-mask.enabled 是否为 true。
2. 当前接口是否显式标注了 @PermissionScene。
3. 当前 scene 是否在 policy.scene 中。
4. PermissionContext.field_policies 是否有对应 field_name。
5. DTO 字段名和 field_name 不一致时，是否加了 @PermissionField。
6. 业务返回是否经过 Jackson 序列化。
```

## 10. 与其他模块的关系

### 10.1 和 SQL 过滤

SQL 过滤不依赖 `@PermissionScene`。

即使普通列表接口没有场景注解，SQL 行级过滤仍然执行：

```text
tenant_id = ?
AND dept_id IN (?, ?)
```

### 10.2 和导出控制

导出接口需要显式声明：

```java
@PermissionScene(PermissionScenes.EXPORT)
```

后续导出控制模块会基于 `EXPORT` 场景匹配 `export_policies`。

### 10.3 和 Web 拦截器

Web 拦截器负责请求结束时清理：

```text
PermissionContextHolder
DataPermissionSceneHolder
```

AOP 也需要在方法执行结束后恢复旧场景，防止嵌套调用污染场景。

## 11. 当前 MVP 结论

```text
普通列表接口不写 @PermissionScene，字段策略不处理。
详情接口显式写 @PermissionScene(DETAIL)。
导出接口显式写 @PermissionScene(EXPORT)。
@PermissionScene 没有默认值，必须显式传值。
@PermissionField 用来声明权限字段名。
字段匹配优先级：@PermissionField > Jackson 输出字段名 > Java 字段名。
当前字段策略按 field_name + scene 匹配。
HIDE 返回 null。
MASK 当前支持 PHONE、EMAIL、ID_CARD、TOKEN、ACCESS_KEY 和默认脱敏。
DENY 抛权限异常。
```

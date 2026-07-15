# SQL 行级权限过滤流程

## 1. 目标

SQL 行级权限过滤用于在业务 SQL 执行前，自动追加当前用户的数据范围条件。

当前 MVP 只追加两个过滤字段：

```sql
tenant_id = ?
AND dept_id IN (?, ?)
```

当前阶段不做字段脱敏、导出控制、审计上报，这些能力走独立模块。

## 2. 前置条件

业务服务需要开启：

```yaml
data-permission:
  enabled: true
  web:
    enabled: true
  sql:
    enabled: true
```

其中：

- `web.enabled=true`：请求进入时获取权限上下文，并写入 `PermissionContextHolder`。
- `sql.enabled=true`：注册 SQL 改写引擎和 MyBatis 拦截器。

业务服务请求需要携带身份 Header：

```text
X-Tenant-Id
X-User-Id
X-Dept-Id
X-Role-Id
```

权限服务返回的上下文中，`resource_object` 里的 `resource_id` 必须等于 SQL 物理表名。

示例：

```json
{
  "tenant_id": "ddd111",
  "dept_scope": ["d001", "d002"],
  "resource_object": {
    "doris": ["security_log"],
    "postgre_sql": ["asset"]
  }
}
```

表示 SDK 会处理：

```text
security_log
asset
```

## 3. 整体流程

```text
HTTP 请求进入业务服务
  -> DataPermissionWebInterceptor
  -> 提取请求身份
  -> 调用权限服务获取 PermissionContext
  -> PermissionContextHolder.set(context)

业务代码执行 Mapper
  -> MyBatis 生成 BoundSql
  -> DataPermissionMyBatisInterceptor 拦截 prepare
  -> 从 PermissionContextHolder 读取上下文
  -> 从 DataPermissionSceneHolder 读取场景，默认 QUERY
  -> 调用 SqlRewriteEngine.rewrite(...)

SQL 改写引擎
  -> JSqlParser 解析 SQL
  -> SqlTableExtractor 提取表名并判断 SQL 结构
  -> PermissionResourceMatcher 判断表名是否在 resource_object 中
  -> PermissionFilterBuilder 构造 tenant_id/dept_id 过滤条件
  -> AST 追加 WHERE 条件
  -> 返回改写后的 SQL 和新增参数

MyBatis 拦截器
  -> 替换 BoundSql.sql
  -> 追加 ParameterMapping
  -> 写入 additionalParameters
  -> MyBatis 继续执行 SQL
```

## 4. rewrite 方法步骤

核心入口：

```java
DefaultSqlRewriteEngine.rewrite(String sql, PermissionContext context, String scene)
```

执行步骤：

```text
no.1 判断 SQL 是否为空
      空 SQL 没有改写意义，直接返回 skipped。

no.2 使用 JSqlParser 把 SQL 文本解析成 AST
      后续只操作 AST，不直接拼接 SQL 字符串。

no.3 解析失败时打印 WARN 日志并放行
      因为解析失败时无法可靠提取表名，也无法判断是否命中 resource_object。

no.4 从 AST 中提取表名，并判断 SQL 是否属于 MVP 支持范围
      当前只支持单表 SELECT / COUNT。

no.5 如果 SQL 结构不支持
      如果涉及 resource_object 中的表，默认拒绝。
      如果不涉及 resource_object 中的表，直接放行。

no.6 判断表名是否在 PermissionContext.resource_object 中
      不在 resource_object 中，说明 SDK 当前不处理这张表，直接放行。

no.7 表名命中后，构造权限过滤条件
      使用 PermissionContext.tenantId 和 PermissionContext.deptScope。

no.8 把权限条件追加到 WHERE 上
      原 SQL 没有 WHERE：新增 WHERE。
      原 SQL 有 WHERE：追加 AND。

no.9 返回改写结果
      返回改写后的 SQL、命中表名、新增参数列表。
```

## 5. 放行和拒绝规则

### 5.1 直接放行

以下情况不改写 SQL，直接放行：

```text
SQL 为空
SQL 解析失败
SQL 表名不在 resource_object 中
SQL 结构不支持，但没有涉及 resource_object 中的表
```

解析失败时会打印 WARN 日志：

```text
SQL permission rewrite skipped because SQL parse failed
```

### 5.2 默认拒绝

以下情况默认拒绝：

```text
PermissionContext 不存在
deny_all = true
tenant_id 为空
dept_scope 为空
SQL 结构不支持，且涉及 resource_object 中的表
```

拒绝时会抛出权限异常，由 Web 层转换成 403。

## 6. SQL 支持范围

当前支持：

```text
单表 SELECT
单表 COUNT
原 SQL 无 WHERE 时新增 WHERE
原 SQL 有 WHERE 时追加 AND
```

暂不支持：

```text
JOIN
UNION
WITH / CTE
子查询
GROUP BY / HAVING
INSERT / UPDATE / DELETE
复杂聚合查询
```

## 7. 改写示例

### 7.1 无 WHERE

原 SQL：

```sql
SELECT * FROM security_log
```

权限上下文：

```json
{
  "tenant_id": "ddd111",
  "dept_scope": ["d001", "d002"],
  "resource_object": {
    "doris": ["security_log"]
  }
}
```

改写后：

```sql
SELECT * FROM security_log
WHERE tenant_id = ?
  AND dept_id IN (?, ?)
```

新增参数：

```text
__dp_tenant_id = ddd111
__dp_dept_id_0 = d001
__dp_dept_id_1 = d002
```

### 7.2 已有 WHERE

原 SQL：

```sql
SELECT * FROM security_log WHERE status = ?
```

改写后：

```sql
SELECT * FROM security_log
WHERE (status = ?)
  AND tenant_id = ?
  AND dept_id IN (?, ?)
```

原业务参数保持不变，权限参数追加在后面。

### 7.3 表名不在 resource_object 中

原 SQL：

```sql
SELECT * FROM dict_area
```

权限上下文：

```json
{
  "resource_object": {
    "doris": ["security_log"]
  }
}
```

处理结果：

```text
dict_area 不在 resource_object 中，SQL 不改写，直接放行。
```

## 8. MyBatis 参数处理

SQL 改写后会多出新的 `?` 占位符，因此 MyBatis 拦截器必须同时做两件事：

```text
1. 替换 BoundSql.sql
2. 追加 ParameterMapping 和 additionalParameters
```

例如新增：

```text
__dp_tenant_id
__dp_dept_id_0
__dp_dept_id_1
```

这些参数会写入：

```java
boundSql.setAdditionalParameter(...)
```

否则 SQL 里新增了 `?`，但 MyBatis 不知道如何绑定参数，执行时会报参数数量不匹配。

## 9. 当前实现类

| 类 | 作用 |
| --- | --- |
| `DataPermissionSqlAutoConfiguration` | SQL 过滤自动装配 |
| `DataPermissionMyBatisInterceptor` | MyBatis SQL 拦截入口 |
| `DefaultSqlRewriteEngine` | SQL 改写核心流程 |
| `SqlTableExtractor` | 提取表名并判断 SQL 支持范围 |
| `PermissionResourceMatcher` | 判断表名是否在 `resource_object` 中 |
| `PermissionFilterBuilder` | 构造 `tenant_id` 和 `dept_id` 过滤条件 |
| `SqlRewriteResult` | 保存改写结果和新增参数 |
| `DataPermissionSceneHolder` | 保存当前请求场景，默认 `QUERY` |

## 10. 当前 MVP 结论

```text
resource_id 必须等于 SQL 物理表名。
表名不在 resource_object 中直接放行。
表名在 resource_object 中追加 tenant_id AND dept_id。
解析失败打印 WARN 后放行。
SQL 结构不支持且涉及受控表时拒绝。
MyBatis 拦截器负责替换 SQL 并追加参数。
```

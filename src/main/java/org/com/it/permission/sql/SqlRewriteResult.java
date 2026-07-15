package org.com.it.permission.sql;

import java.util.Collections;
import java.util.List;

/**
 * SQL 权限改写结果。
 */
public class SqlRewriteResult {

    /** true 表示 SQL 已经追加权限条件，调用方需要替换 SQL 并追加参数。 */
    private final boolean rewritten;

    /** 改写后的 SQL；未改写时就是原 SQL。 */
    private final String sql;

    /** 命中的物理表名，未改写时为空。 */
    private final String tableName;

    /** SDK 新增的权限参数，顺序和 SQL 中新增的 ? 保持一致。 */
    private final List<PermissionSqlParameter> additionalParameters;

    /**
     * 创建 SQL 改写结果。
     */
    private SqlRewriteResult(boolean rewritten,
                             String sql,
                             String tableName,
                             List<PermissionSqlParameter> additionalParameters) {
        this.rewritten = rewritten;
        this.sql = sql;
        this.tableName = tableName;
        this.additionalParameters = additionalParameters;
    }

    /**
     * 创建未改写结果。
     */
    public static SqlRewriteResult skipped(String sql) {
        // 表名不在 resource_object 中，或者解析失败按 MVP 规则放行时使用这个结果。
        return new SqlRewriteResult(false, sql, null, Collections.emptyList());
    }

    /**
     * 创建已改写结果。
     */
    public static SqlRewriteResult rewritten(String sql,
                                             String tableName,
                                             List<PermissionSqlParameter> additionalParameters) {
        return new SqlRewriteResult(true, sql, tableName, List.copyOf(additionalParameters));
    }

    /**
     * 判断 SQL 是否已被 SDK 改写。
     */
    public boolean isRewritten() {
        return rewritten;
    }

    /**
     * 返回最终 SQL。
     */
    public String getSql() {
        return sql;
    }

    /**
     * 返回命中的物理表名。
     */
    public String getTableName() {
        return tableName;
    }

    /**
     * 返回 SDK 追加的权限参数。
     */
    public List<PermissionSqlParameter> getAdditionalParameters() {
        return additionalParameters;
    }
}

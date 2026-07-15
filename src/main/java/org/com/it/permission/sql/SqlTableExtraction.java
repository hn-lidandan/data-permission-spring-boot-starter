package org.com.it.permission.sql;

import net.sf.jsqlparser.statement.select.PlainSelect;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * SQL 表提取结果。
 */
class SqlTableExtraction {

    /** true 表示当前 SQL 是 MVP 支持的单表查询，可以安全追加 WHERE 条件。 */
    private final boolean supported;

    /** 支持改写时的单表物理表名。 */
    private final String tableName;

    /** 支持改写时保留 PlainSelect 引用，外层引擎会直接在这个 AST 节点上设置 WHERE。 */
    private final PlainSelect plainSelect;

    /** 不支持改写时仍尽量收集表名，用于判断是否涉及 resource_object 中的资源。 */
    private final Set<String> tableNames;

    /** 不支持原因，用于权限拒绝异常和后续排查。 */
    private final String unsupportedReason;

    /**
     * 创建 SQL 表提取结果。
     */
    private SqlTableExtraction(boolean supported,
                               String tableName,
                               PlainSelect plainSelect,
                               Set<String> tableNames,
                               String unsupportedReason) {
        this.supported = supported;
        this.tableName = tableName;
        this.plainSelect = plainSelect;
        this.tableNames = tableNames;
        this.unsupportedReason = unsupportedReason;
    }

    /**
     * 创建支持改写的单表查询结果。
     */
    static SqlTableExtraction supported(String tableName, PlainSelect plainSelect) {
        Set<String> tableNames = new LinkedHashSet<>();
        tableNames.add(tableName);
        return new SqlTableExtraction(true, tableName, plainSelect, tableNames, null);
    }

    /**
     * 创建不支持改写的 SQL 结构结果。
     */
    static SqlTableExtraction unsupported(Set<String> tableNames, String reason) {
        return new SqlTableExtraction(false, null, null, Collections.unmodifiableSet(tableNames), reason);
    }

    /**
     * 判断当前 SQL 是否属于安全支持范围。
     */
    boolean isSupported() {
        return supported;
    }

    /**
     * 返回支持改写时的单表表名。
     */
    String getTableName() {
        return tableName;
    }

    /**
     * 返回支持改写时的 PlainSelect AST 节点。
     */
    PlainSelect getPlainSelect() {
        return plainSelect;
    }

    /**
     * 返回从 SQL 中尽量提取出的表名集合。
     */
    Set<String> getTableNames() {
        return tableNames;
    }

    /**
     * 返回 SQL 不支持改写的原因。
     */
    String getUnsupportedReason() {
        return unsupportedReason;
    }
}

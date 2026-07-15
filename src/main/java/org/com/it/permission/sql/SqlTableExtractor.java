package org.com.it.permission.sql;

import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.ExpressionVisitorAdapter;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.delete.Delete;
import net.sf.jsqlparser.statement.insert.Insert;
import net.sf.jsqlparser.statement.select.FromItem;
import net.sf.jsqlparser.statement.select.Join;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.SelectBody;
import net.sf.jsqlparser.statement.select.SetOperationList;
import net.sf.jsqlparser.statement.select.SubSelect;
import net.sf.jsqlparser.statement.update.Update;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 从 SQL AST 中提取表名，并判断当前结构是否属于 MVP 支持范围。
 */
public class SqlTableExtractor {

    /**
     * 从 SQL AST 提取表信息，并判断是否可安全改写。
     */
    public SqlTableExtraction extract(Statement statement) {
        // DML 语句当前不做改写；如果表名命中 resource_object，外层引擎会默认拒绝。
        if (statement instanceof Select select) {
            return extractSelect(select);
        }
        if (statement instanceof Insert insert) {
            return SqlTableExtraction.unsupported(tableSet(insert.getTable()), "INSERT is not supported");
        }
        if (statement instanceof Update update) {
            return SqlTableExtraction.unsupported(tableSet(update.getTable()), "UPDATE is not supported");
        }
        if (statement instanceof Delete delete) {
            return SqlTableExtraction.unsupported(tableSet(delete.getTable()), "DELETE is not supported");
        }
        return SqlTableExtraction.unsupported(new LinkedHashSet<>(), "Only SELECT is supported");
    }

    /**
     * 处理 SELECT 语句，区分普通单表查询和 UNION/CTE 等复杂结构。
     */
    private SqlTableExtraction extractSelect(Select select) {
        // WITH/CTE 可能隐藏多层查询和多个表，一期不做安全改写。
        if (!CollectionUtils.isEmpty(select.getWithItemsList())) {
            return SqlTableExtraction.unsupported(collectTables(select.getSelectBody()), "WITH/CTE is not supported");
        }

        SelectBody selectBody = select.getSelectBody();
        if (selectBody instanceof PlainSelect plainSelect) {
            return extractPlainSelect(plainSelect);
        }
        if (selectBody instanceof SetOperationList setOperationList) {
            // UNION 可能同时访问多个表，一期只提取表名用于判断是否命中权限资源。
            Set<String> tableNames = new LinkedHashSet<>();
            for (SelectBody body : setOperationList.getSelects()) {
                tableNames.addAll(collectTables(body));
            }
            return SqlTableExtraction.unsupported(tableNames, "UNION/SET operation is not supported");
        }
        return SqlTableExtraction.unsupported(new LinkedHashSet<>(), "Unsupported SELECT body");
    }

    /**
     * 判断 PlainSelect 是否满足 MVP 支持的单表查询约束。
     */
    private SqlTableExtraction extractPlainSelect(PlainSelect plainSelect) {
        Set<String> tableNames = collectTables(plainSelect);
        FromItem fromItem = plainSelect.getFromItem();
        // MVP 只支持 FROM 后面直接跟物理表；子查询作为 FROM 时暂不改写。
        if (!(fromItem instanceof Table table)) {
            return SqlTableExtraction.unsupported(tableNames, "Subquery FROM is not supported");
        }
        if (!CollectionUtils.isEmpty(plainSelect.getJoins())) {
            return SqlTableExtraction.unsupported(tableNames, "JOIN is not supported");
        }
        // GROUP BY/HAVING 场景下追加过滤可能影响聚合语义，先收紧为不支持。
        if (plainSelect.getGroupBy() != null || plainSelect.getHaving() != null) {
            return SqlTableExtraction.unsupported(tableNames, "GROUP BY/HAVING is not supported");
        }
        if (containsSubSelect(plainSelect.getWhere())) {
            return SqlTableExtraction.unsupported(tableNames, "Subquery WHERE is not supported");
        }

        String tableName = normalizeTableName(table);
        if (!StringUtils.hasText(tableName)) {
            return SqlTableExtraction.unsupported(tableNames, "Table name is empty");
        }
        return SqlTableExtraction.supported(tableName, plainSelect);
    }

    /**
     * 从 SELECT body 中收集表名。
     */
    private Set<String> collectTables(SelectBody selectBody) {
        if (selectBody instanceof PlainSelect plainSelect) {
            return collectTables(plainSelect);
        }
        return new LinkedHashSet<>();
    }

    /**
     * 从普通 SELECT 中收集 FROM 和 JOIN 涉及的表名。
     */
    private Set<String> collectTables(PlainSelect plainSelect) {
        Set<String> tableNames = new LinkedHashSet<>();
        addTableName(tableNames, plainSelect.getFromItem());
        if (!CollectionUtils.isEmpty(plainSelect.getJoins())) {
            // 即使 JOIN 不支持，也要收集右表，供外层判断 JOIN 是否涉及受控资源。
            for (Join join : plainSelect.getJoins()) {
                addTableName(tableNames, join.getRightItem());
            }
        }
        return tableNames;
    }

    /**
     * 把单个 Table 转成表名集合。
     */
    private Set<String> tableSet(Table table) {
        Set<String> tableNames = new LinkedHashSet<>();
        String tableName = normalizeTableName(table);
        if (StringUtils.hasText(tableName)) {
            tableNames.add(tableName);
        }
        return tableNames;
    }

    /**
     * 如果 FROM/JOIN 节点是物理表，则加入表名集合。
     */
    private void addTableName(Set<String> tableNames, FromItem fromItem) {
        if (fromItem instanceof Table table) {
            String tableName = normalizeTableName(table);
            if (StringUtils.hasText(tableName)) {
                tableNames.add(tableName);
            }
        }
    }

    /**
     * 提取物理表名。
     */
    private String normalizeTableName(Table table) {
        // table.getName() 只取物理表名；public.security_log 会得到 security_log。
        return table == null ? null : table.getName();
    }

    /**
     * 判断 WHERE 表达式里是否包含子查询。
     */
    private boolean containsSubSelect(Expression expression) {
        if (expression == null) {
            return false;
        }
        SubSelectDetector detector = new SubSelectDetector();
        expression.accept(detector);
        return detector.isDetected();
    }

    private static class SubSelectDetector extends ExpressionVisitorAdapter {

        private boolean detected;

        /**
         * 访问到子查询表达式时打标。
         */
        @Override
        public void visit(SubSelect subSelect) {
            // WHERE 中存在子查询时，一期不尝试递归改写。
            detected = true;
        }

        /**
         * 返回是否检测到子查询。
         */
        boolean isDetected() {
            return detected;
        }
    }
}

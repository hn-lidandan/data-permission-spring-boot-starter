package org.com.it.permission.sql;

import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.Parenthesis;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.PlainSelect;
import org.com.it.permission.exception.UnsupportedPermissionSqlException;
import org.com.it.permission.model.PermissionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import java.util.Set;

/**
 * 基于 JSqlParser 的默认 SQL 行级权限改写引擎。
 */
public class DefaultSqlRewriteEngine implements SqlRewriteEngine {

    private static final Logger log = LoggerFactory.getLogger(DefaultSqlRewriteEngine.class);

    private static final int SQL_LOG_LIMIT = 500;

    private final SqlTableExtractor tableExtractor;

    private final PermissionResourceMatcher resourceMatcher;

    private final PermissionFilterBuilder filterBuilder;

    /**
     * 创建默认 SQL 改写引擎。
     */
    public DefaultSqlRewriteEngine(SqlTableExtractor tableExtractor,
                                   PermissionResourceMatcher resourceMatcher,
                                   PermissionFilterBuilder filterBuilder) {
        this.tableExtractor = tableExtractor;
        this.resourceMatcher = resourceMatcher;
        this.filterBuilder = filterBuilder;
    }

    /**
     * 执行 SQL 权限改写。
     */
    @Override
    public SqlRewriteResult rewrite(String sql, PermissionContext context, String scene) {
        // no.1 空 SQL 没有改写意义，直接按未改写结果返回。
        if (!StringUtils.hasText(sql)) {
            return SqlRewriteResult.skipped(sql);
        }

        Statement statement;
        try {
            // no.2 先把文本 SQL 解析成 AST，后续只操作 AST，避免直接字符串拼接导致语法错误。
            statement = CCJSqlParserUtil.parse(sql);
        } catch (JSQLParserException ex) {
            // no.3 解析失败时无法可靠提取表名，也就无法判断是否命中 resource_object；当前 MVP 约定 warn 后放行。
            log.warn("SQL permission rewrite skipped because SQL parse failed, scene={}, reason={}, sql={}",
                    StringUtils.hasText(scene) ? scene : "QUERY",
                    ex.getMessage(),
                    abbreviate(sql));
            return SqlRewriteResult.skipped(sql);
        }

        // no.4 从 AST 中提取表名，并判断 SQL 是否属于当前 MVP 支持的单表查询范围。
        SqlTableExtraction extraction = tableExtractor.extract(statement);
        if (!extraction.isSupported()) {
            // no.5 能解析但 SQL 结构超出 MVP 支持范围时，只有命中权限资源才拒绝；非权限资源仍放行。
            rejectIfControlledResource(context, extraction.getTableNames(), extraction.getUnsupportedReason());
            return SqlRewriteResult.skipped(sql);
        }

        // no.6 判断当前单表是否在 PermissionContext.resource_object 中；不在集合内说明 SDK 当前不处理这张表。
        String tableName = extraction.getTableName();
        if (!resourceMatcher.matches(context, tableName)) {
            return SqlRewriteResult.skipped(sql);
        }

        // no.7 表名命中后，根据权限上下文构造 tenant_id 和 dept_id 的过滤条件以及对应参数。
        PermissionFilter filter = filterBuilder.build(context);
        PlainSelect plainSelect = extraction.getPlainSelect();
        Expression oldWhere = plainSelect.getWhere();

        // no.8 把权限条件挂到 WHERE 上：无 WHERE 时新建 WHERE，有 WHERE 时追加 AND。
        plainSelect.setWhere(oldWhere == null
                ? filter.getExpression()
                // 原 WHERE 可能包含 OR，外层加括号后再 AND 权限条件，避免改变业务条件优先级。
                : new AndExpression(new Parenthesis(oldWhere), filter.getExpression()));

        // no.9 返回改写后的 SQL 和新增参数，MyBatis 拦截器会负责把它们写回 BoundSql。
        return SqlRewriteResult.rewritten(statement.toString(), tableName, filter.getParameters());
    }

    /**
     * 不支持的 SQL 如果涉及受控资源，则直接拒绝。
     */
    private void rejectIfControlledResource(PermissionContext context, Set<String> tableNames, String reason) {
        // 例如 JOIN 里只要出现 security_log 这类受控表，就不能跳过权限过滤继续执行原 SQL。
        boolean controlled = tableNames.stream()
                .anyMatch(tableName -> resourceMatcher.matches(context, tableName));
        if (controlled) {
            throw new UnsupportedPermissionSqlException("Unsupported permission SQL: " + reason);
        }
    }

    /**
     * 生成用于日志输出的 SQL 摘要。
     */
    private String abbreviate(String sql) {
        // 日志只打 SQL 摘要，避免超长 SQL 或敏感条件把日志打爆。
        String normalized = sql.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= SQL_LOG_LIMIT) {
            return normalized;
        }
        return normalized.substring(0, SQL_LOG_LIMIT) + "...";
    }
}

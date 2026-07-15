package org.com.it.permission.sql;

import net.sf.jsqlparser.expression.Expression;

import java.util.List;

/**
 * 权限过滤条件表达式和对应的新增参数。
 */
class PermissionFilter {

    private final Expression expression;

    private final List<PermissionSqlParameter> parameters;

    /**
     * 创建权限过滤条件。
     */
    PermissionFilter(Expression expression, List<PermissionSqlParameter> parameters) {
        this.expression = expression;
        this.parameters = parameters;
    }

    /**
     * 返回要挂到 WHERE 上的权限表达式。
     */
    Expression getExpression() {
        return expression;
    }

    /**
     * 返回权限表达式对应的新增参数。
     */
    List<PermissionSqlParameter> getParameters() {
        return parameters;
    }
}

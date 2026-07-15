package org.com.it.permission.sql;

import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.JdbcParameter;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.expression.operators.relational.EqualsTo;
import net.sf.jsqlparser.expression.operators.relational.ExpressionList;
import net.sf.jsqlparser.expression.operators.relational.InExpression;
import net.sf.jsqlparser.schema.Column;
import org.com.it.permission.exception.PermissionDeniedException;
import org.com.it.permission.model.PermissionContext;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 根据权限上下文构造 tenant_id 和 dept_id 过滤条件。
 */
public class PermissionFilterBuilder {

    private static final String TENANT_PARAMETER = "__dp_tenant_id";

    private static final String DEPT_PARAMETER_PREFIX = "__dp_dept_id_";

    /**
     * 根据权限上下文构造权限过滤表达式和参数。
     */
    public PermissionFilter build(PermissionContext context) {
        // 命中权限资源后，权限上下文必须完整；缺少租户或部门范围时默认拒绝。
        if (context == null || context.isDenyAll()) {
            throw new PermissionDeniedException("Permission context denied");
        }
        if (!StringUtils.hasText(context.getTenantId())) {
            throw new PermissionDeniedException("Missing permission context field: tenant_id");
        }
        if (context.getDeptScope() == null || context.getDeptScope().isEmpty()) {
            throw new PermissionDeniedException("Missing permission context field: dept_scope");
        }

        List<PermissionSqlParameter> parameters = new ArrayList<>();
        parameters.add(new PermissionSqlParameter(TENANT_PARAMETER, context.getTenantId(), String.class));

        // tenant_id = ?
        EqualsTo tenantExpression = new EqualsTo(new Column("tenant_id"), new JdbcParameter());

        // dept_id IN (?, ?, ...)
        List<Expression> deptValues = new ArrayList<>();
        for (int i = 0; i < context.getDeptScope().size(); i++) {
            String deptId = context.getDeptScope().get(i);
            if (!StringUtils.hasText(deptId)) {
                throw new PermissionDeniedException("Missing permission context field: dept_scope");
            }
            parameters.add(new PermissionSqlParameter(DEPT_PARAMETER_PREFIX + i, deptId, String.class));
            deptValues.add(new JdbcParameter());
        }

        // 当前 MVP 只做 tenant_id 和 dept_id，两者固定为 AND 关系。
        InExpression deptExpression = new InExpression(
                new Column("dept_id"),
                new ExpressionList(deptValues)
        );
        return new PermissionFilter(new AndExpression(tenantExpression, deptExpression), parameters);
    }
}

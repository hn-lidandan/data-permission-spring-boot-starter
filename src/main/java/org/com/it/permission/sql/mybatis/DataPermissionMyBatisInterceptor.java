package org.com.it.permission.sql.mybatis;

import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.apache.ibatis.session.Configuration;
import org.com.it.permission.context.DataPermissionSceneHolder;
import org.com.it.permission.context.PermissionContextHolder;
import org.com.it.permission.model.PermissionContext;
import org.com.it.permission.sql.PermissionSqlParameter;
import org.com.it.permission.sql.SqlRewriteEngine;
import org.com.it.permission.sql.SqlRewriteResult;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;

/**
 * MyBatis SQL 拦截器，在 PreparedStatement 创建前追加数据权限过滤条件。
 */
@Intercepts({
        @Signature(type = StatementHandler.class, method = "prepare", args = {Connection.class, Integer.class})
})
public class DataPermissionMyBatisInterceptor implements Interceptor {

    private final SqlRewriteEngine rewriteEngine;

    /**
     * 创建 MyBatis SQL 权限拦截器。
     */
    public DataPermissionMyBatisInterceptor(SqlRewriteEngine rewriteEngine) {
        this.rewriteEngine = rewriteEngine;
    }

    /**
     * 拦截 MyBatis prepare 阶段，完成 SQL 改写和参数追加。
     */
    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        StatementHandler statementHandler = (StatementHandler) invocation.getTarget();
        BoundSql boundSql = statementHandler.getBoundSql();
        // SQL 过滤只能在请求线程已经绑定权限上下文后执行；缺失时沿用默认拒绝。
        PermissionContext context = PermissionContextHolder.require();
        String scene = DataPermissionSceneHolder.current();

        SqlRewriteResult result = rewriteEngine.rewrite(boundSql.getSql(), context, scene);
        if (result.isRewritten()) {
            applyRewrite(statementHandler, boundSql, result);
        }
        return invocation.proceed();
    }

    /**
     * 将改写后的 SQL 和新增参数写回 MyBatis BoundSql。
     */
    private void applyRewrite(StatementHandler statementHandler,
                              BoundSql boundSql,
                              SqlRewriteResult result) {
        MetaObject boundSqlMetaObject = SystemMetaObject.forObject(boundSql);
        // BoundSql 没有公开 setSql 方法，只能通过 MyBatis MetaObject 修改内部 sql 字段。
        boundSqlMetaObject.setValue("sql", result.getSql());

        List<ParameterMapping> parameterMappings = new ArrayList<>(boundSql.getParameterMappings());
        Configuration configuration = findConfiguration(statementHandler);
        for (PermissionSqlParameter parameter : result.getAdditionalParameters()) {
            // SQL 多了几个 ?，这里必须同步追加 ParameterMapping，否则 JDBC 绑定参数数量会不匹配。
            parameterMappings.add(new ParameterMapping.Builder(
                    configuration,
                    parameter.getName(),
                    parameter.getJavaType()
            ).build());
            // 追加参数值放到 additionalParameters，MyBatis ParameterHandler 会按同名 ParameterMapping 读取。
            boundSql.setAdditionalParameter(parameter.getName(), parameter.getValue());
        }
        boundSqlMetaObject.setValue("parameterMappings", parameterMappings);
    }

    /**
     * 从 StatementHandler 中获取 MyBatis Configuration。
     */
    private Configuration findConfiguration(StatementHandler statementHandler) {
        MetaObject metaObject = SystemMetaObject.forObject(statementHandler);
        // RoutingStatementHandler 会把真实 StatementHandler 放在 delegate 里；测试或特殊场景可能直接持有 configuration。
        if (metaObject.hasGetter("delegate.configuration")) {
            return (Configuration) metaObject.getValue("delegate.configuration");
        }
        return (Configuration) metaObject.getValue("configuration");
    }
}

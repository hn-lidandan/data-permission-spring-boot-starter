package org.com.it.permission.autoconfigure;

import org.com.it.permission.context.PermissionContextManager;
import org.com.it.permission.sql.DefaultSqlRewriteEngine;
import org.com.it.permission.sql.PermissionFilterBuilder;
import org.com.it.permission.sql.PermissionResourceMatcher;
import org.com.it.permission.sql.SqlRewriteEngine;
import org.com.it.permission.sql.SqlTableExtractor;
import org.com.it.permission.sql.mybatis.DataPermissionMyBatisInterceptor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * SQL 行级权限过滤自动装配。
 */
@AutoConfiguration(after = DataPermissionAutoConfiguration.class)
// SDK 总开关打开后，SQL 子模块才有机会生效。
@ConditionalOnProperty(prefix = "data-permission", name = "enabled", havingValue = "true")
// SQL 行级过滤默认关闭，业务服务明确配置 data-permission.sql.enabled=true 后再注册。
@ConditionalOnProperty(prefix = "data-permission.sql", name = "enabled", havingValue = "true")
// 没有 JSqlParser 时不注册 SQL 改写能力，避免非 SQL 场景引入 starter 后缺依赖。
@ConditionalOnClass(name = "net.sf.jsqlparser.parser.CCJSqlParserUtil")
// SQL 过滤依赖请求入口先初始化 PermissionContext。
@ConditionalOnBean(PermissionContextManager.class)
public class DataPermissionSqlAutoConfiguration {

    /**
     * 注册 SQL 表名提取器。
     */
    @Bean
    @ConditionalOnMissingBean
    public SqlTableExtractor sqlTableExtractor() {
        return new SqlTableExtractor();
    }

    /**
     * 注册 resource_object 资源匹配器。
     */
    @Bean
    @ConditionalOnMissingBean
    public PermissionResourceMatcher permissionResourceMatcher() {
        return new PermissionResourceMatcher();
    }

    /**
     * 注册权限过滤条件构造器。
     */
    @Bean
    @ConditionalOnMissingBean
    public PermissionFilterBuilder permissionFilterBuilder() {
        return new PermissionFilterBuilder();
    }

    /**
     * 注册 SQL 改写核心引擎。
     */
    @Bean
    @ConditionalOnMissingBean
    public SqlRewriteEngine sqlRewriteEngine(SqlTableExtractor tableExtractor,
                                             PermissionResourceMatcher resourceMatcher,
                                             PermissionFilterBuilder filterBuilder) {
        // 核心改写引擎不依赖 MyBatis，后续接 JDBC 或其他 ORM 时可以复用。
        return new DefaultSqlRewriteEngine(tableExtractor, resourceMatcher, filterBuilder);
    }

    @Configuration(proxyBeanMethods = false)
    // MyBatis 是 optional 依赖；只有宿主业务服务真正引入 MyBatis 时才创建拦截器。
    @ConditionalOnClass(name = "org.apache.ibatis.plugin.Interceptor")
    static class MyBatisConfiguration {

        /**
         * 宿主服务存在 MyBatis 时注册 MyBatis SQL 拦截器。
         */
        @Bean
        @ConditionalOnMissingBean
        public DataPermissionMyBatisInterceptor dataPermissionMyBatisInterceptor(SqlRewriteEngine rewriteEngine) {
            return new DataPermissionMyBatisInterceptor(rewriteEngine);
        }
    }
}

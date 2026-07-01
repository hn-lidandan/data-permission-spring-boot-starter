package org.com.it.permission.autoconfigure;

import org.com.it.permission.config.DataPermissionProperties;
import org.com.it.permission.context.PermissionContextManager;
import org.com.it.permission.identity.HeaderPermissionIdentityExtractor;
import org.com.it.permission.identity.PermissionIdentityExtractor;
import org.com.it.permission.web.DataPermissionWebMvcConfigurer;
import org.com.it.permission.web.DataPermissionWebInterceptor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * Web 场景自动装配。
 *
 * <p>只有宿主业务服务存在 Spring MVC 相关类、SDK 总开关打开、并且 {@code data-permission.web.enabled}
 * 没有关掉时，才注册 Web 拦截器。这样 starter 可以被非 Web 服务引入，而不会强制要求 Web 环境。</p>
 */
@AutoConfiguration(after = DataPermissionAutoConfiguration.class)
@ConditionalOnProperty(prefix = "data-permission", name = "enabled", havingValue = "true")
@ConditionalOnClass(name = {
        "jakarta.servlet.http.HttpServletRequest",
        "org.springframework.web.servlet.HandlerInterceptor",
        "org.springframework.web.servlet.config.annotation.WebMvcConfigurer"
})
@ConditionalOnBean(PermissionContextManager.class)
public class DataPermissionWebAutoConfiguration {

    /**
     * 默认身份提取器。
     *
     * <p>一期先从请求 Header 读取身份。生产接入时，这些 Header 应由网关或认证组件写入，
     * 不能直接信任前端随意传入的 Header。</p>
     */
    @Bean
    @ConditionalOnMissingBean
    public PermissionIdentityExtractor permissionIdentityExtractor(DataPermissionProperties properties) {
        return new HeaderPermissionIdentityExtractor(properties);
    }

    /**
     * 请求拦截器：请求进入时绑定权限上下文，请求结束时清理 ThreadLocal。
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "data-permission.web", name = "enabled", havingValue = "true", matchIfMissing = true)
    public DataPermissionWebInterceptor dataPermissionWebInterceptor(PermissionIdentityExtractor identityExtractor,
                                                                     PermissionContextManager contextManager) {
        return new DataPermissionWebInterceptor(identityExtractor, contextManager);
    }

    /**
     * 把 SDK 拦截器注册到 Spring MVC 拦截器链。
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "data-permission.web", name = "enabled", havingValue = "true", matchIfMissing = true)
    public DataPermissionWebMvcConfigurer dataPermissionWebMvcConfigurer(DataPermissionWebInterceptor interceptor,
                                                                         DataPermissionProperties properties) {
        return new DataPermissionWebMvcConfigurer(interceptor, properties);
    }
}

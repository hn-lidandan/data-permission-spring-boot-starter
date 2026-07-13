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
// Web 自动装配依赖核心自动装配中的 PermissionContextManager，所以放在核心自动装配之后执行。
@AutoConfiguration(after = DataPermissionAutoConfiguration.class)
// SDK 总开关关闭时，整个 Web 场景的自动装配都不生效。
@ConditionalOnProperty(prefix = "data-permission", name = "enabled", havingValue = "true")
// 只有 Spring MVC Web 应用才会存在这些类；非 Web 服务引入 starter 时会自动跳过本配置。
@ConditionalOnClass(name = {
        "jakarta.servlet.http.HttpServletRequest",
        "org.springframework.web.servlet.HandlerInterceptor",
        "org.springframework.web.servlet.config.annotation.WebMvcConfigurer"
})
// 确认核心权限上下文管理器已存在，再注册 Web 请求入口相关组件。
@ConditionalOnBean(PermissionContextManager.class)
public class DataPermissionWebAutoConfiguration {

    /**
     * 默认身份提取器。
     *
     * <p>一期先从请求 Header 读取身份。生产接入时，这些 Header 应由网关或认证组件写入，
     * 不能直接信任前端随意传入的 Header。</p>
     */
    @Bean
    // 业务服务可以自行提供 PermissionIdentityExtractor，覆盖 SDK 默认的 Header 提取方式。
    @ConditionalOnMissingBean
    public PermissionIdentityExtractor permissionIdentityExtractor(DataPermissionProperties properties) {
        return new HeaderPermissionIdentityExtractor(properties);
    }

    /**
     * 请求拦截器：请求进入时绑定权限上下文，请求结束时清理 ThreadLocal。
     */
    @Bean
    // 如果业务服务已经声明自己的 DataPermissionWebInterceptor，就不再创建默认拦截器。
    @ConditionalOnMissingBean
    // data-permission.web.enabled 默认为 true；只有显式配置 false 时才关闭 Web 拦截能力。
    @ConditionalOnProperty(prefix = "data-permission.web", name = "enabled", havingValue = "true", matchIfMissing = true)
    public DataPermissionWebInterceptor dataPermissionWebInterceptor(PermissionIdentityExtractor identityExtractor,
                                                                     PermissionContextManager contextManager) {
        // 拦截器组合身份提取器和上下文管理器：先解析当前用户身份，再获取并绑定权限上下文。
        return new DataPermissionWebInterceptor(identityExtractor, contextManager);
    }

    /**
     * 把 SDK 拦截器注册到 Spring MVC 拦截器链。
     */
    @Bean
    // 如果业务服务已经提供同类型注册器，默认注册器不重复创建，避免重复注册拦截器。
    @ConditionalOnMissingBean
    // 与拦截器 Bean 使用相同开关，避免只注册了配置器但没有拦截器的半启用状态。
    @ConditionalOnProperty(prefix = "data-permission.web", name = "enabled", havingValue = "true", matchIfMissing = true)
    public DataPermissionWebMvcConfigurer dataPermissionWebMvcConfigurer(DataPermissionWebInterceptor interceptor,
                                                                         DataPermissionProperties properties) {
        // 注册器内部会读取 data-permission.web.path-pattern 和 exclude-path-patterns。
        return new DataPermissionWebMvcConfigurer(interceptor, properties);
    }
}

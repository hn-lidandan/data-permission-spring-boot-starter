package org.com.it.permission.web;

import org.com.it.permission.config.DataPermissionProperties;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Spring MVC 拦截器注册器。
 *
 * <p>把 SDK 的 Web 拦截器加入宿主业务服务的 MVC 拦截链，并支持配置拦截路径和排除路径。</p>
 */
public class DataPermissionWebMvcConfigurer implements WebMvcConfigurer {

    private final DataPermissionWebInterceptor interceptor;

    private final DataPermissionProperties properties;

    public DataPermissionWebMvcConfigurer(DataPermissionWebInterceptor interceptor, DataPermissionProperties properties) {
        this.interceptor = interceptor;
        this.properties = properties;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        DataPermissionProperties.WebProperties web = properties.getWeb();
        registry.addInterceptor(interceptor)
                .addPathPatterns(web.getPathPattern())
                .excludePathPatterns(web.getExcludePathPatterns());
    }
}

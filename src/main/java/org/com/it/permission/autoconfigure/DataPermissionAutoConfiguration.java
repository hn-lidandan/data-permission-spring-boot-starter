package org.com.it.permission.autoconfigure;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.com.it.permission.cache.CaffeinePermissionContextCache;
import org.com.it.permission.cache.PermissionContextCache;
import org.com.it.permission.config.DataPermissionProperties;
import org.com.it.permission.context.DefaultPermissionContextManager;
import org.com.it.permission.context.PermissionContextManager;
import org.com.it.permission.model.PermissionContext;
import org.com.it.permission.service.HttpPermissionServiceClient;
import org.com.it.permission.service.PermissionServiceClient;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.util.StringUtils;

import java.time.Duration;

/**
 * SDK 核心自动装配。
 *
 * <p>只有业务服务显式配置 {@code data-permission.enabled=true} 时，这里的核心 Bean 才会注册。
 * 这一层只放和 Web、Kafka、SQL 等具体接入方式无关的基础能力，例如配置校验、权限上下文缓存、
 * 权限上下文管理器、SDK 内置权限服务客户端。</p>
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "data-permission", name = "enabled", havingValue = "true")
public class DataPermissionAutoConfiguration {

    /**
     * 启动期校验关键配置。
     *
     * <p>关闭 SDK 时不会进入这个自动装配类，所以不会校验 service-url；开启 SDK 后必须提供权限服务地址。
     * 这能避免业务服务启动成功，但第一次请求进来才发现权限服务地址缺失。</p>
     */
    @Bean
    public InitializingBean dataPermissionPropertiesValidator(DataPermissionProperties properties) {
        return () -> {
            if (!StringUtils.hasText(properties.getServiceUrl())) {
                throw new IllegalStateException("data-permission.service-url must not be empty when data-permission.enabled=true");
            }
            if (properties.getCache().getExpireSeconds() <= 0) {
                throw new IllegalStateException("data-permission.cache.expire-seconds must be greater than 0");
            }
            if (properties.getCache().getMaximumSize() <= 0) {
                throw new IllegalStateException("data-permission.cache.maximum-size must be greater than 0");
            }
            if (properties.getClient().getConnectTimeoutMillis() <= 0) {
                throw new IllegalStateException("data-permission.client.connect-timeout-millis must be greater than 0");
            }
            if (properties.getClient().getRequestTimeoutMillis() <= 0) {
                throw new IllegalStateException("data-permission.client.request-timeout-millis must be greater than 0");
            }
        };
    }

    /**
     * Caffeine 原生缓存对象。
     *
     * <p>这里暴露的是底层 Cache，方便业务服务在特殊场景下通过同类型 Bean 替换默认缓存配置。</p>
     */
    @Bean
    @ConditionalOnMissingBean
    public Cache<String, PermissionContext> permissionContextCaffeineCache(DataPermissionProperties properties) {
        DataPermissionProperties.CacheProperties cache = properties.getCache();
        return Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofSeconds(cache.getExpireSeconds()))
                .maximumSize(cache.getMaximumSize())
                .build();
    }

    /**
     * SDK 内部使用的权限上下文缓存抽象。
     *
     * <p>后续如果要切换缓存实现，只需要替换这个接口实现，不影响 ContextManager。</p>
     */
    @Bean
    @ConditionalOnMissingBean
    public PermissionContextCache permissionContextCache(Cache<String, PermissionContext> permissionContextCaffeineCache) {
        return new CaffeinePermissionContextCache(permissionContextCaffeineCache);
    }

    /**
     * SDK 内置权限服务客户端。
     *
     * <p>业务服务不需要自己调用权限服务。开启 SDK 后，这个 Bean 会读取
     * {@code data-permission.service-url}，由 SDK 统一向权限管理服务发起 POST 请求获取上下文。</p>
     */
    @Bean
    @Primary
    public PermissionServiceClient permissionServiceClient(DataPermissionProperties properties) {
        return new HttpPermissionServiceClient(properties);
    }

    /**
     * 权限上下文管理器：负责缓存命中、缓存未命中回源、拒绝态处理。
     */
    @Bean
    @ConditionalOnMissingBean
    public PermissionContextManager permissionContextManager(PermissionContextCache cache,
                                                             PermissionServiceClient client,
                                                             DataPermissionProperties properties) {
        return new DefaultPermissionContextManager(cache, client, properties);
    }

}

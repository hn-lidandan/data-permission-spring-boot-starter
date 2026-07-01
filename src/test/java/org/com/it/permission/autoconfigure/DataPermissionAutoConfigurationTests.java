package org.com.it.permission.autoconfigure;

import org.com.it.permission.cache.PermissionContextCache;
import org.com.it.permission.config.DataPermissionProperties;
import org.com.it.permission.context.PermissionContextManager;
import org.com.it.permission.identity.PermissionIdentity;
import org.com.it.permission.identity.PermissionIdentityExtractor;
import org.com.it.permission.model.PermissionContext;
import org.com.it.permission.service.HttpPermissionServiceClient;
import org.com.it.permission.service.PermissionServiceClient;
import org.com.it.permission.web.DataPermissionWebInterceptor;
import org.com.it.permission.web.DataPermissionWebMvcConfigurer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 自动装配测试。
 *
 * <p>这组测试验证 starter 最核心的接入行为：业务服务只通过配置开关控制 SDK Bean 是否注册；
 * 开启后由 SDK 自动注册内置 HTTP Client 去调用权限服务。</p>
 */
class DataPermissionAutoConfigurationTests {

    /**
     * ApplicationContextRunner 可以只启动一个轻量 Spring 容器，用来测试自动装配，
     * 不需要真的启动一个 Web 服务。
     */
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    DataPermissionPropertiesAutoConfiguration.class,
                    DataPermissionAutoConfiguration.class,
                    DataPermissionWebAutoConfiguration.class
            ));

    @Test
    void shouldNotRegisterCoreBeansWhenDisabled() {
        // 总开关关闭时，只保留配置绑定能力，不注册缓存、上下文管理器和 Web 拦截器。
        contextRunner
                .withPropertyValues("data-permission.enabled=false")
                .run(context -> {
                    assertThat(context).hasSingleBean(DataPermissionProperties.class);
                    assertThat(context).doesNotHaveBean(PermissionContextCache.class);
                    assertThat(context).doesNotHaveBean(PermissionContextManager.class);
                    assertThat(context).doesNotHaveBean(DataPermissionWebInterceptor.class);
                });
    }

    @Test
    void shouldRegisterCoreBeansWhenEnabled() {
        // 总开关打开且 service-url 配置完整时，SDK 核心 Bean、内置 HTTP Client 和 Web Bean 都应该自动注册。
        contextRunner
                .withPropertyValues(
                        "data-permission.enabled=true",
                        "data-permission.service-url=http://data-permission-service",
                        "data-permission.client-app=log-service"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(DataPermissionProperties.class);
                    assertThat(context).hasSingleBean(PermissionContextCache.class);
                    assertThat(context).hasSingleBean(PermissionContextManager.class);
                    assertThat(context).hasSingleBean(PermissionIdentityExtractor.class);
                    assertThat(context).hasSingleBean(PermissionServiceClient.class);
                    assertThat(context.getBean(PermissionServiceClient.class))
                            .isInstanceOf(HttpPermissionServiceClient.class);
                    assertThat(context).hasSingleBean(DataPermissionWebInterceptor.class);
                    assertThat(context).hasSingleBean(DataPermissionWebMvcConfigurer.class);
                });
    }

    @Test
    void shouldNotRegisterWebBeansWhenWebDisabled() {
        // Web 子开关关闭时，核心上下文能力仍然存在，但不会拦截 HTTP 请求。
        contextRunner
                .withPropertyValues(
                        "data-permission.enabled=true",
                        "data-permission.service-url=http://data-permission-service",
                        "data-permission.web.enabled=false"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(PermissionContextManager.class);
                    assertThat(context).doesNotHaveBean(DataPermissionWebInterceptor.class);
                    assertThat(context).doesNotHaveBean(DataPermissionWebMvcConfigurer.class);
                });
    }

    @Test
    void shouldBindProperties() {
        // 验证 data-permission.* 配置能正确绑定到 DataPermissionProperties。
        contextRunner
                .withPropertyValues(
                        "data-permission.enabled=true",
                        "data-permission.service-url=http://data-permission-service",
                        "data-permission.client-app=log-service",
                        "data-permission.cache.expire-seconds=120",
                        "data-permission.cache.maximum-size=200",
                        "data-permission.client.connect-timeout-millis=1000",
                        "data-permission.client.request-timeout-millis=2000",
                        "data-permission.sql.enabled=true",
                        "data-permission.sql.fail-on-unsupported-sql=true",
                        "data-permission.sql.resources.security-log.resource-type=DORIS",
                        "data-permission.sql.resources.security-log.resource-id=security_log",
                        "data-permission.sql.resources.security-log.table-names[0]=normalized_security_log",
                        "data-permission.kafka.enabled=true",
                        "data-permission.kafka.invalidate-topic=auth-context-invalidate",
                        "data-permission.kafka.audit-topic=data-permission-audit-topic"
                )
                .run(context -> {
                    DataPermissionProperties properties = context.getBean(DataPermissionProperties.class);

                    assertThat(properties.isEnabled()).isTrue();
                    assertThat(properties.getServiceUrl()).isEqualTo("http://data-permission-service");
                    assertThat(properties.getClientApp()).isEqualTo("log-service");
                    assertThat(properties.getCache().getExpireSeconds()).isEqualTo(120);
                    assertThat(properties.getCache().getMaximumSize()).isEqualTo(200);
                    assertThat(properties.getClient().getConnectTimeoutMillis()).isEqualTo(1000);
                    assertThat(properties.getClient().getRequestTimeoutMillis()).isEqualTo(2000);
                    assertThat(properties.getSql().isEnabled()).isTrue();
                    assertThat(properties.getSql().getResources())
                            .containsKey("security-log");
                    assertThat(properties.getSql().getResources().get("security-log").getResourceType())
                            .isEqualTo("DORIS");
                    assertThat(properties.getSql().getResources().get("security-log").getTableNames())
                            .containsExactly("normalized_security_log");
                    assertThat(properties.getKafka().isEnabled()).isTrue();
                });
    }

    @Test
    void shouldFailWhenEnabledWithoutServiceUrl() {
        // SDK 开启后必须提供权限服务地址，否则启动期直接失败，避免运行时才暴露配置错误。
        contextRunner
                .withPropertyValues("data-permission.enabled=true")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasMessageContaining("data-permission.service-url must not be empty");
                });
    }

    @Test
    void shouldKeepSdkHttpClientAsPrimaryServiceClient() {
        // 权限服务调用是 SDK 的职责。即使容器里存在其他 PermissionServiceClient，主链路也应该使用 SDK 内置 HTTP Client。
        contextRunner
                .withUserConfiguration(ExtraClientConfiguration.class)
                .withPropertyValues(
                        "data-permission.enabled=true",
                        "data-permission.service-url=http://data-permission-service"
                )
                .run(context -> {
                    assertThat(context).hasBean("permissionServiceClient");
                    assertThat(context.getBean(PermissionServiceClient.class))
                            .isInstanceOf(HttpPermissionServiceClient.class);
                    assertThat(context.getBeansOfType(PermissionServiceClient.class))
                            .hasSize(2);
                });
    }

    @Configuration(proxyBeanMethods = false)
    static class ExtraClientConfiguration {

        /**
         * 模拟业务容器里意外出现的同类型 Bean，用来验证 SDK 内置客户端仍然是主客户端。
         */
        @Bean
        PermissionServiceClient extraPermissionServiceClient() {
            return identity -> {
                PermissionContext context = new PermissionContext();
                context.setTenantId(identity.getTenantId());
                context.setUserId(identity.getUserId());
                context.setExpiresAt(LocalDateTime.now().plusMinutes(1));
                return context;
            };
        }
    }
}

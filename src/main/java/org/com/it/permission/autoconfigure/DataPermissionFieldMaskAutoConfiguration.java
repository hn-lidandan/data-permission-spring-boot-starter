package org.com.it.permission.autoconfigure;

import org.com.it.permission.context.PermissionContextManager;
import org.com.it.permission.masking.DataMaskingEngine;
import org.com.it.permission.masking.FieldMaskStartupDiagnostic;
import org.com.it.permission.masking.FieldPolicyMatcher;
import org.com.it.permission.masking.PermissionBeanSerializerModifier;
import org.com.it.permission.masking.PermissionJacksonModule;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter;

/**
 * 字段策略自动装配。
 */
@AutoConfiguration(after = DataPermissionAutoConfiguration.class)
@ConditionalOnProperty(prefix = "data-permission", name = "enabled", havingValue = "true")
@ConditionalOnProperty(prefix = "data-permission.field-mask", name = "enabled", havingValue = "true")
@ConditionalOnClass(name = "tools.jackson.databind.json.JsonMapper")
@ConditionalOnBean(PermissionContextManager.class)
public class DataPermissionFieldMaskAutoConfiguration {

    /**
     * 注册字段策略匹配器。
     */
    @Bean
    @ConditionalOnMissingBean
    public FieldPolicyMatcher fieldPolicyMatcher() {
        return new FieldPolicyMatcher();
    }

    /**
     * 注册字段策略执行引擎。
     */
    @Bean
    @ConditionalOnMissingBean
    public DataMaskingEngine dataMaskingEngine(FieldPolicyMatcher matcher) {
        return new DataMaskingEngine(matcher);
    }

    /**
     * 注册 Jackson Bean 序列化修改器。
     */
    @Bean
    @ConditionalOnMissingBean
    public PermissionBeanSerializerModifier permissionBeanSerializerModifier(DataMaskingEngine maskingEngine) {
        return new PermissionBeanSerializerModifier(maskingEngine);
    }

    /**
     * 注册 Jackson Module，Spring Boot 4 会把 JacksonModule Bean 自动加入自动配置的 JsonMapper。
     */
    @Bean
    @ConditionalOnMissingBean
    public PermissionJacksonModule permissionJacksonModule(PermissionBeanSerializerModifier serializerModifier) {
        return new PermissionJacksonModule(serializerModifier);
    }

    /**
     * 启动自检：应用就绪后确认 MVC 响应序列化用的 ObjectMapper 已注册字段权限 Module。
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnClass(name = "org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter")
    public FieldMaskStartupDiagnostic fieldMaskStartupDiagnostic(ObjectProvider<RequestMappingHandlerAdapter> handlerAdapters) {
        return new FieldMaskStartupDiagnostic(handlerAdapters);
    }
}

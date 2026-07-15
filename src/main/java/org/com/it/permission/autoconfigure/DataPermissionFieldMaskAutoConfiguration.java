package org.com.it.permission.autoconfigure;

import org.com.it.permission.context.PermissionContextManager;
import org.com.it.permission.masking.DataMaskingEngine;
import org.com.it.permission.masking.FieldPolicyMatcher;
import org.com.it.permission.masking.PermissionBeanSerializerModifier;
import org.com.it.permission.masking.PermissionJacksonModule;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * 字段策略自动装配。
 */
@AutoConfiguration(after = DataPermissionAutoConfiguration.class)
@ConditionalOnProperty(prefix = "data-permission", name = "enabled", havingValue = "true")
@ConditionalOnProperty(prefix = "data-permission.field-mask", name = "enabled", havingValue = "true")
@ConditionalOnClass(name = "com.fasterxml.jackson.databind.ObjectMapper")
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
     * 注册 Jackson Module，Spring Boot 会把 Module 自动加入业务 ObjectMapper。
     */
    @Bean
    @ConditionalOnMissingBean
    public PermissionJacksonModule permissionJacksonModule(PermissionBeanSerializerModifier serializerModifier) {
        return new PermissionJacksonModule(serializerModifier);
    }
}

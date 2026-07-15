package org.com.it.permission.autoconfigure;

import org.com.it.permission.scene.PermissionSceneAspect;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * 数据权限场景注解自动装配。
 */
@AutoConfiguration(after = DataPermissionAutoConfiguration.class)
@ConditionalOnProperty(prefix = "data-permission", name = "enabled", havingValue = "true")
@ConditionalOnClass(name = "org.aspectj.lang.annotation.Aspect")
public class DataPermissionSceneAutoConfiguration {

    /**
     * 注册 @PermissionScene 场景切面。
     */
    @Bean
    @ConditionalOnMissingBean
    public PermissionSceneAspect permissionSceneAspect() {
        return new PermissionSceneAspect();
    }
}

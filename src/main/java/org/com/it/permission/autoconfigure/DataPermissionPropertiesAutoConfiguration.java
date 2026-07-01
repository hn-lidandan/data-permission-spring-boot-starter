package org.com.it.permission.autoconfigure;

import org.com.it.permission.config.DataPermissionProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * 配置属性自动装配。
 *
 * <p>这个类不受 {@code data-permission.enabled} 控制，目的是让业务服务即使关闭 SDK，
 * 也可以正常绑定和检查 {@code data-permission.*} 配置；真正的核心 Bean 注册由
 * {@link DataPermissionAutoConfiguration} 控制。</p>
 */
@AutoConfiguration
@EnableConfigurationProperties(DataPermissionProperties.class)
public class DataPermissionPropertiesAutoConfiguration {
}

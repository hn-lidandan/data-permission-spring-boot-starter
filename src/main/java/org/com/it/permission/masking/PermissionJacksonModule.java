package org.com.it.permission.masking;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.module.SimpleModule;

/**
 * Jackson 字段权限模块。
 *
 * <p>Spring Boot 4 会把容器里的 {@code tools.jackson.databind.JacksonModule} Bean
 * 自动装进自动配置的 {@code JsonMapper}。</p>
 */
public class PermissionJacksonModule extends SimpleModule {

    private static final Logger log = LoggerFactory.getLogger(PermissionJacksonModule.class);

    /**
     * 创建 Jackson 模块并注册字段序列化修改器。
     */
    public PermissionJacksonModule(PermissionBeanSerializerModifier serializerModifier) {
        super("data-permission-field-mask");
        setSerializerModifier(serializerModifier);
        log.info("[data-permission] PermissionJacksonModule bean created");
    }

    /**
     * Module 被某个 Mapper 注册时回调；没有这条日志说明 Module 没进对应的 Mapper。
     */
    @Override
    public void setupModule(SetupContext context) {
        super.setupModule(context);
        log.info("[data-permission] PermissionJacksonModule registered into a mapper (owner: {})",
                context.getOwner() == null ? "unknown" : context.getOwner().getClass().getName());
    }
}

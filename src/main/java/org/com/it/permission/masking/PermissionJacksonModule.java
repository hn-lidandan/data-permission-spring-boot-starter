package org.com.it.permission.masking;

import com.fasterxml.jackson.databind.module.SimpleModule;

/**
 * Jackson 字段权限模块。
 */
public class PermissionJacksonModule extends SimpleModule {

    /**
     * 创建 Jackson 模块并注册字段序列化修改器。
     */
    public PermissionJacksonModule(PermissionBeanSerializerModifier serializerModifier) {
        super("data-permission-field-mask");
        setSerializerModifier(serializerModifier);
    }
}

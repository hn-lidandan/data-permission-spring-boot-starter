package org.com.it.permission.masking;

import org.com.it.permission.context.DataPermissionSceneHolder;
import org.com.it.permission.context.PermissionContextHolder;
import org.com.it.permission.model.PermissionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ser.BeanPropertyWriter;

import java.util.Optional;

/**
 * 包装 Jackson 字段序列化逻辑，在有场景时应用字段策略。
 */
class PermissionFieldBeanPropertyWriter extends BeanPropertyWriter {

    private static final Logger log = LoggerFactory.getLogger(PermissionFieldBeanPropertyWriter.class);

    private final DataMaskingEngine maskingEngine;

    private final String permissionFieldName;

    /**
     * 创建字段权限序列化 Writer。
     */
    PermissionFieldBeanPropertyWriter(BeanPropertyWriter base,
                                      DataMaskingEngine maskingEngine,
                                      String permissionFieldName) {
        super(base);
        this.maskingEngine = maskingEngine;
        this.permissionFieldName = permissionFieldName;
    }

    /**
     * 序列化字段时根据当前场景和字段策略决定输出值。
     */
    @Override
    public void serializeAsProperty(Object bean, JsonGenerator gen, SerializationContext ctxt) throws Exception {
        // no.1 先读取当前请求场景；普通列表接口没有 @PermissionScene 时，这里拿不到 scene。
        Optional<String> scene = DataPermissionSceneHolder.get();
        if (scene.isEmpty()) {
            // no.2 没有场景就不执行字段策略，直接交回 Jackson 原始字段序列化逻辑。
            log.info("[data-permission] serialize field [{}] (permissionField=[{}]): scene is EMPTY at serialization time, "
                            + "masking SKIPPED. contextPresent={}",
                    getName(), permissionFieldName, PermissionContextHolder.get().isPresent());
            super.serializeAsProperty(bean, gen, ctxt);
            return;
        }

        // no.3 有场景时读取当前权限上下文，字段策略只从当前请求绑定的 PermissionContext 中获取。
        PermissionContext context = PermissionContextHolder.require();

        // no.4 读取当前字段的原始值，并按 permissionFieldName + scene 匹配 field_policies。
        Object originalValue = get(bean);
        FieldMaskingResult result = maskingEngine.mask(permissionFieldName, originalValue, context, scene.get());
        if (!result.isApplied()) {
            // no.5 没命中字段策略时，保持原始输出，不改变字段值和 Jackson 默认行为。
            log.info("[data-permission] serialize field [{}] (permissionField=[{}], scene=[{}]): no field policy matched, "
                            + "masking NOT applied. fieldPolicies size={}",
                    getName(), permissionFieldName, scene.get(),
                    context.getFieldPolicies() == null ? 0 : context.getFieldPolicies().size());
            super.serializeAsProperty(bean, gen, ctxt);
            return;
        }

        // no.6 命中字段策略时，输出原字段名，但字段值使用 MASK/HIDE 等策略处理后的结果。
        log.info("[data-permission] serialize field [{}] (permissionField=[{}], scene=[{}]): field policy matched, masking APPLIED",
                getName(), permissionFieldName, scene.get());
        gen.writeName(getName());
        Object maskedValue = result.getValue();
        if (maskedValue == null) {
            ctxt.defaultSerializeNullValue(gen);
        } else {
            ctxt.writeValue(gen, maskedValue);
        }
    }
}

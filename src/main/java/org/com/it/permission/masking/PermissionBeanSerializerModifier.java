package org.com.it.permission.masking;

import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.SerializationConfig;
import com.fasterxml.jackson.databind.introspect.AnnotatedMember;
import com.fasterxml.jackson.databind.ser.BeanPropertyWriter;
import com.fasterxml.jackson.databind.ser.BeanSerializerModifier;

import java.util.ArrayList;
import java.util.List;

/**
 * 为 Jackson Bean 字段替换支持权限策略的 Writer。
 */
public class PermissionBeanSerializerModifier extends BeanSerializerModifier {

    private final DataMaskingEngine maskingEngine;

    /**
     * 创建字段权限序列化修改器。
     */
    public PermissionBeanSerializerModifier(DataMaskingEngine maskingEngine) {
        this.maskingEngine = maskingEngine;
    }

    /**
     * 包装 Bean 的每一个字段 Writer，使其在有场景时可以执行字段策略。
     */
    @Override
    public List<BeanPropertyWriter> changeProperties(SerializationConfig config,
                                                     BeanDescription beanDesc,
                                                     List<BeanPropertyWriter> beanProperties) {
        List<BeanPropertyWriter> writers = new ArrayList<>(beanProperties.size());
        for (BeanPropertyWriter writer : beanProperties) {
            writers.add(new PermissionFieldBeanPropertyWriter(
                    writer,
                    maskingEngine,
                    resolvePermissionFieldName(writer)
            ));
        }
        return writers;
    }

    /**
     * 解析权限字段名，优先使用 @PermissionField。
     */
    private String resolvePermissionFieldName(BeanPropertyWriter writer) {
        PermissionField annotation = writer.getAnnotation(PermissionField.class);
        AnnotatedMember member = writer.getMember();
        if (annotation == null && member != null) {
            annotation = member.getAnnotation(PermissionField.class);
        }
        if (annotation != null) {
            return annotation.value();
        }
        return writer.getName();
    }
}

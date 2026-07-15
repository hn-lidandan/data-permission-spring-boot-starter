package org.com.it.permission.masking;

import org.com.it.permission.exception.PermissionDeniedException;
import org.com.it.permission.model.FieldPolicy;
import org.com.it.permission.model.PermissionContext;
import org.com.it.permission.model.PermissionType;

import java.util.Optional;

/**
 * 字段策略执行引擎。
 */
public class DataMaskingEngine {

    private final FieldPolicyMatcher matcher;

    /**
     * 创建字段策略执行引擎。
     */
    public DataMaskingEngine(FieldPolicyMatcher matcher) {
        this.matcher = matcher;
    }

    /**
     * 根据字段名、当前值、权限上下文和场景处理字段值。
     */
    FieldMaskingResult mask(String fieldName, Object value, PermissionContext context, String scene) {
        Optional<FieldPolicy> policy = matcher.match(context, fieldName, scene);
        if (policy.isEmpty()) {
            return FieldMaskingResult.unchanged(value);
        }

        FieldPolicy fieldPolicy = policy.get();
        PermissionType type = fieldPolicy.getPermissionType() == null ? PermissionType.SHOW : fieldPolicy.getPermissionType();
        return switch (type) {
            case SHOW -> FieldMaskingResult.unchanged(value);
            case MASK -> FieldMaskingResult.applied(MaskRules.mask(fieldPolicy.getMaskRegex(), value));
            case HIDE -> FieldMaskingResult.applied(null);
            case DENY -> throw new PermissionDeniedException("Field access denied: " + fieldName);
        };
    }
}

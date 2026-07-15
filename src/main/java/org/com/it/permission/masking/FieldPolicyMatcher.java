package org.com.it.permission.masking;

import org.com.it.permission.model.FieldPolicy;
import org.com.it.permission.model.PermissionContext;
import org.com.it.permission.model.PermissionType;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * 按 field_name + scene 匹配字段策略。
 */
public class FieldPolicyMatcher {

    /**
     * 从权限上下文中查找当前字段和场景对应的策略。
     */
    public Optional<FieldPolicy> match(PermissionContext context, String fieldName, String scene) {
        if (context == null || !StringUtils.hasText(fieldName) || !StringUtils.hasText(scene)) {
            return Optional.empty();
        }
        List<FieldPolicy> policies = context.getFieldPolicies();
        if (CollectionUtils.isEmpty(policies)) {
            return Optional.empty();
        }

        String normalizedFieldName = normalize(fieldName);
        String normalizedScene = normalize(scene);
        return policies.stream()
                .filter(policy -> policy != null && StringUtils.hasText(policy.getFieldName()))
                .filter(policy -> normalize(policy.getFieldName()).equals(normalizedFieldName))
                .filter(policy -> matchesScene(policy, normalizedScene))
                .max(Comparator.comparingInt(this::permissionPriority));
    }

    /**
     * 判断字段策略是否适用于当前场景。
     */
    private boolean matchesScene(FieldPolicy policy, String normalizedScene) {
        if (CollectionUtils.isEmpty(policy.getScene())) {
            return true;
        }
        return policy.getScene().stream()
                .filter(StringUtils::hasText)
                .map(FieldPolicyMatcher::normalize)
                .anyMatch(scene -> "all".equals(scene) || scene.equals(normalizedScene));
    }

    /**
     * 返回权限动作优先级，多个策略同时匹配时选择更严格的动作。
     */
    private int permissionPriority(FieldPolicy policy) {
        PermissionType type = policy.getPermissionType() == null ? PermissionType.SHOW : policy.getPermissionType();
        return switch (type) {
            case DENY -> 4;
            case HIDE -> 3;
            case MASK -> 2;
            case SHOW -> 1;
        };
    }

    /**
     * 归一化字段名和场景，降低大小写差异带来的匹配问题。
     */
    private static String normalize(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }
}

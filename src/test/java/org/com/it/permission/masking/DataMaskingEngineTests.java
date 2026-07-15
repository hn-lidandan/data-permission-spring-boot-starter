package org.com.it.permission.masking;

import org.com.it.permission.exception.PermissionDeniedException;
import org.com.it.permission.model.FieldPolicy;
import org.com.it.permission.model.PermissionContext;
import org.com.it.permission.model.PermissionType;
import org.com.it.permission.scene.PermissionScenes;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 字段策略执行引擎测试。
 */
class DataMaskingEngineTests {

    private final DataMaskingEngine maskingEngine = new DataMaskingEngine(new FieldPolicyMatcher());

    @Test
    void shouldMaskPhoneWhenSceneMatches() {
        PermissionContext context = context(policy("phone", PermissionType.MASK, "PHONE", List.of(PermissionScenes.DETAIL)));

        FieldMaskingResult result = maskingEngine.mask("phone", "13812345678", context, PermissionScenes.DETAIL);

        assertThat(result.isApplied()).isTrue();
        assertThat(result.getValue()).isEqualTo("138****5678");
    }

    @Test
    void shouldMaskEmailWhenSceneMatches() {
        PermissionContext context = context(policy("email", PermissionType.MASK, "EMAIL", List.of(PermissionScenes.DETAIL)));

        FieldMaskingResult result = maskingEngine.mask("email", "zhangsan@example.com", context, PermissionScenes.DETAIL);

        assertThat(result.isApplied()).isTrue();
        assertThat(result.getValue()).isEqualTo("z****@example.com");
    }

    @Test
    void shouldMaskIdCardWhenSceneMatches() {
        PermissionContext context = context(policy("id_card", PermissionType.MASK, "ID_CARD", List.of(PermissionScenes.DETAIL)));

        FieldMaskingResult result = maskingEngine.mask("id_card", "430123199901011234", context, PermissionScenes.DETAIL);

        assertThat(result.isApplied()).isTrue();
        assertThat(result.getValue()).isEqualTo("430***********1234");
    }

    @Test
    void shouldMaskTokenWhenSceneMatches() {
        PermissionContext context = context(policy("token", PermissionType.MASK, "TOKEN", List.of(PermissionScenes.DETAIL)));

        FieldMaskingResult result = maskingEngine.mask("token", "sk-abcdefghijklmnopqrstuvwxyz", context, PermissionScenes.DETAIL);

        assertThat(result.isApplied()).isTrue();
        assertThat(result.getValue()).isEqualTo("sk-************");
    }

    @Test
    void shouldMaskAccessKeyWhenSceneMatches() {
        PermissionContext context = context(policy("access_key", PermissionType.MASK, "ACCESS_KEY", List.of(PermissionScenes.DETAIL)));

        FieldMaskingResult result = maskingEngine.mask("access_key", "AKIA1234567890ABCDEFG", context, PermissionScenes.DETAIL);

        assertThat(result.isApplied()).isTrue();
        assertThat(result.getValue()).isEqualTo("AKIA************");
    }

    @Test
    void shouldHideFieldWhenSceneMatches() {
        PermissionContext context = context(policy("raw_log", PermissionType.HIDE, null, List.of(PermissionScenes.EXPORT)));

        FieldMaskingResult result = maskingEngine.mask("raw_log", "raw", context, PermissionScenes.EXPORT);

        assertThat(result.isApplied()).isTrue();
        assertThat(result.getValue()).isNull();
    }

    @Test
    void shouldSkipWhenSceneDoesNotMatch() {
        PermissionContext context = context(policy("phone", PermissionType.MASK, "PHONE", List.of(PermissionScenes.DETAIL)));

        FieldMaskingResult result = maskingEngine.mask("phone", "13812345678", context, PermissionScenes.EXPORT);

        assertThat(result.isApplied()).isFalse();
        assertThat(result.getValue()).isEqualTo("13812345678");
    }

    @Test
    void shouldTreatEmptyPolicySceneAsAllScenes() {
        PermissionContext context = context(policy("phone", PermissionType.MASK, "PHONE", List.of()));

        FieldMaskingResult result = maskingEngine.mask("phone", "13812345678", context, PermissionScenes.EXPORT);

        assertThat(result.isApplied()).isTrue();
        assertThat(result.getValue()).isEqualTo("138****5678");
    }

    @Test
    void shouldDenyFieldWhenPolicyIsDeny() {
        PermissionContext context = context(policy("secret", PermissionType.DENY, null, List.of(PermissionScenes.DETAIL)));

        assertThatThrownBy(() -> maskingEngine.mask("secret", "value", context, PermissionScenes.DETAIL))
                .isInstanceOf(PermissionDeniedException.class)
                .hasMessageContaining("secret");
    }

    private PermissionContext context(FieldPolicy... policies) {
        PermissionContext context = new PermissionContext();
        context.setFieldPolicies(List.of(policies));
        return context;
    }

    private FieldPolicy policy(String fieldName, PermissionType type, String maskRegex, List<String> scenes) {
        FieldPolicy policy = new FieldPolicy();
        policy.setFieldName(fieldName);
        policy.setPermissionType(type);
        policy.setMaskRegex(maskRegex);
        policy.setScene(scenes);
        return policy;
    }
}

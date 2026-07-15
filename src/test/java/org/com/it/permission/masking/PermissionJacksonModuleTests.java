package org.com.it.permission.masking;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.com.it.permission.context.DataPermissionSceneHolder;
import org.com.it.permission.context.PermissionContextHolder;
import org.com.it.permission.model.FieldPolicy;
import org.com.it.permission.model.PermissionContext;
import org.com.it.permission.model.PermissionType;
import org.com.it.permission.scene.PermissionScenes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Jackson 字段策略模块测试。
 */
class PermissionJacksonModuleTests {

    private final ObjectMapper objectMapper = createObjectMapper();

    @AfterEach
    void tearDown() {
        PermissionContextHolder.clear();
        DataPermissionSceneHolder.clear();
    }

    @Test
    void shouldSkipFieldPoliciesWhenSceneIsMissing() throws Exception {
        PermissionContextHolder.set(context());

        String json = objectMapper.writeValueAsString(new LogDetailVO("13812345678", "raw", "zhangsan"));

        assertThat(json).contains("\"mobile\":\"13812345678\"");
        assertThat(json).contains("\"rawLog\":\"raw\"");
    }

    @Test
    void shouldApplyDetailFieldPolicies() throws Exception {
        PermissionContextHolder.set(context());
        DataPermissionSceneHolder.set(PermissionScenes.DETAIL);

        String json = objectMapper.writeValueAsString(new LogDetailVO("13812345678", "raw", "zhangsan"));

        assertThat(json).contains("\"mobile\":\"138****5678\"");
        assertThat(json).contains("\"rawLog\":null");
        assertThat(json).contains("\"username\":\"zhangsan\"");
    }

    @Test
    void shouldApplyOnlyExportMatchedFieldPolicies() throws Exception {
        PermissionContextHolder.set(context());
        DataPermissionSceneHolder.set(PermissionScenes.EXPORT);

        String json = objectMapper.writeValueAsString(new LogDetailVO("13812345678", "raw", "zhangsan"));

        assertThat(json).contains("\"mobile\":\"13812345678\"");
        assertThat(json).contains("\"rawLog\":null");
    }

    private ObjectMapper createObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        mapper.registerModule(new PermissionJacksonModule(
                new PermissionBeanSerializerModifier(
                        new DataMaskingEngine(new FieldPolicyMatcher())
                )
        ));
        return mapper;
    }

    private PermissionContext context() {
        PermissionContext context = new PermissionContext();
        context.setFieldPolicies(List.of(
                policy("phone", PermissionType.MASK, "PHONE", List.of(PermissionScenes.DETAIL)),
                policy("raw_log", PermissionType.HIDE, null, List.of(PermissionScenes.DETAIL, PermissionScenes.EXPORT))
        ));
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

    static class LogDetailVO {

        @PermissionField("phone")
        public String mobile;

        @PermissionField("raw_log")
        public String rawLog;

        public String username;

        LogDetailVO(String mobile, String rawLog, String username) {
            this.mobile = mobile;
            this.rawLog = rawLog;
            this.username = username;
        }
    }
}

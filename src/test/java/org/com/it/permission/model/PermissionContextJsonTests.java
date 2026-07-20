package org.com.it.permission.model;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 权限上下文 JSON 契约测试。
 *
 * <p>这组测试直接使用你给出的权限服务返回结构，验证 SDK 模型能正确接住 snake_case 字段、
 * 字段策略、导出策略、版本号和带时区的 expires_at。</p>
 */
class PermissionContextJsonTests {

    @Test
    void shouldDeserializePermissionContextFromServiceJson() throws Exception {
        // Jackson 3 内置 Java 8 时间类型支持，不再需要单独注册 JavaTimeModule。
        JsonMapper objectMapper = JsonMapper.builder().build();
        String json = """
                {
                  "user_id": "u001",
                  "tenant_id": "t001",
                  "dept_scope": ["d001", "d002"],
                  "asset_group_scope": ["ag001", "ag002"],
                  "data_level_scope": ["public", "internal"],
                  "resource_object": {
                    "doris": ["security_log", "alert_event"],
                    "postgre_sql": ["asset", "report"]
                  },
                  "field_policies": [
                    {
                      "field_name": "phone",
                      "permission_type": "MASK",
                      "mask_regex": "PHONE",
                      "scene": ["DETAIL"]
                    },
                    {
                      "field_name": "raw_log",
                      "permission_type": "HIDE",
                      "mask_regex": null,
                      "scene": ["DETAIL", "EXPORT"]
                    }
                  ],
                  "export_policies": [
                    {
                      "resource_type": "doris",
                      "resource_id": "security_log",
                      "is_allowed": true,
                      "max_rows": 5000,
                      "need_approval": false
                    }
                  ],
                  "deny_all": false,
                  "version": 12,
                  "expires_at": "2026-06-29T18:00:00+08:00"
                }
                """;

        PermissionContext context = objectMapper.readValue(json, PermissionContext.class);

        // 基础身份和范围字段。
        assertThat(context.getUserId()).isEqualTo("u001");
        assertThat(context.getTenantId()).isEqualTo("t001");
        assertThat(context.getDeptScope()).containsExactly("d001", "d002");
        assertThat(context.getAssetGroupScope()).containsExactly("ag001", "ag002");
        assertThat(context.getDataLevelScope()).containsExactly("public", "internal");

        // 资源对象保持服务端原始 key，后续 SQL 执行层再做资源类型归一化。
        assertThat(context.getResourceObject()).containsEntry("doris", java.util.List.of("security_log", "alert_event"));
        assertThat(context.getResourceObject()).containsEntry("postgre_sql", java.util.List.of("asset", "report"));

        // 字段策略用于后续 Jackson 脱敏/隐藏/拒绝。
        assertThat(context.getFieldPolicies()).hasSize(2);
        assertThat(context.getFieldPolicies().get(0).getFieldName()).isEqualTo("phone");
        assertThat(context.getFieldPolicies().get(0).getPermissionType()).isEqualTo(PermissionType.MASK);
        assertThat(context.getFieldPolicies().get(0).getMaskRegex()).isEqualTo("PHONE");
        assertThat(context.getFieldPolicies().get(0).getScene()).containsExactly("DETAIL");
        assertThat(context.getFieldPolicies().get(1).getFieldName()).isEqualTo("raw_log");
        assertThat(context.getFieldPolicies().get(1).getPermissionType()).isEqualTo(PermissionType.HIDE);
        assertThat(context.getFieldPolicies().get(1).getScene()).containsExactly("DETAIL", "EXPORT");

        // 导出策略用于后续导出 AOP 控制。
        assertThat(context.getExportPolicies()).hasSize(1);
        assertThat(context.getExportPolicies().get(0).getResourceType()).isEqualTo("doris");
        assertThat(context.getExportPolicies().get(0).getResourceId()).isEqualTo("security_log");
        assertThat(context.getExportPolicies().get(0).isAllowed()).isTrue();
        assertThat(context.getExportPolicies().get(0).getMaxRows()).isEqualTo(5000);
        assertThat(context.getExportPolicies().get(0).isNeedApproval()).isFalse();

        // 全局拒绝标记、版本号和过期时间。
        assertThat(context.isDenyAll()).isFalse();
        assertThat(context.getVersion()).isEqualTo(12);
        assertThat(context.getExpiresAt()).isEqualTo(LocalDateTime.parse("2026-06-29T18:00:00"));
    }

    @Test
    void shouldDeserializeRealPermissionServiceResponse() throws Exception {
        // 使用权限服务当前真实返回样例，覆盖微秒级 LocalDateTime 和 export_policies 中 allowed/is_allowed 并存的情况。
        JsonMapper objectMapper = JsonMapper.builder().build();
        String json = """
                {
                  "asset_group_scope": [
                    "ag001",
                    "ag002"
                  ],
                  "data_level_scope": [
                    "public",
                    "internal"
                  ],
                  "deny_all": false,
                  "dept_scope": [
                    "d001",
                    "d002"
                  ],
                  "expires_at": "2026-07-01T11:20:48.762675",
                  "export_policies": [
                    {
                      "allowed": true,
                      "is_allowed": true,
                      "max_rows": 5000,
                      "need_approval": false,
                      "resource_id": "security_log",
                      "resource_type": "doris"
                    }
                  ],
                  "field_policies": [
                    {
                      "field_name": "phone",
                      "mask_regex": "PHONE",
                      "permission_type": "MASK",
                      "scene": [
                        "DETAIL"
                      ]
                    },
                    {
                      "field_name": "raw_log",
                      "mask_regex": null,
                      "permission_type": "HIDE",
                      "scene": [
                        "DETAIL",
                        "EXPORT"
                      ]
                    }
                  ],
                  "resource_object": {
                    "doris": [
                      "security_log",
                      "alert_event"
                    ],
                    "postgre_sql": [
                      "asset",
                      "report"
                    ]
                  },
                  "tenant_id": "ddd111",
                  "user_id": "dfew123",
                  "version": 12
                }
                """;

        PermissionContext context = objectMapper.readValue(json, PermissionContext.class);

        assertThat(context.getTenantId()).isEqualTo("ddd111");
        assertThat(context.getUserId()).isEqualTo("dfew123");
        assertThat(context.getDeptScope()).containsExactly("d001", "d002");
        assertThat(context.getAssetGroupScope()).containsExactly("ag001", "ag002");
        assertThat(context.getDataLevelScope()).containsExactly("public", "internal");
        assertThat(context.getResourceObject()).containsEntry("doris", java.util.List.of("security_log", "alert_event"));
        assertThat(context.getResourceObject()).containsEntry("postgre_sql", java.util.List.of("asset", "report"));
        assertThat(context.getFieldPolicies()).hasSize(2);
        assertThat(context.getFieldPolicies().get(0).getFieldName()).isEqualTo("phone");
        assertThat(context.getFieldPolicies().get(0).getPermissionType()).isEqualTo(PermissionType.MASK);
        assertThat(context.getFieldPolicies().get(1).getFieldName()).isEqualTo("raw_log");
        assertThat(context.getFieldPolicies().get(1).getPermissionType()).isEqualTo(PermissionType.HIDE);
        assertThat(context.getExportPolicies()).hasSize(1);
        assertThat(context.getExportPolicies().get(0).isAllowed()).isTrue();
        assertThat(context.getExportPolicies().get(0).getMaxRows()).isEqualTo(5000);
        assertThat(context.getExportPolicies().get(0).isNeedApproval()).isFalse();
        assertThat(context.isDenyAll()).isFalse();
        assertThat(context.getVersion()).isEqualTo(12);
        assertThat(context.getExpiresAt()).isEqualTo(LocalDateTime.parse("2026-07-01T11:20:48.762675"));
    }
}

package org.com.it.permission.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

/**
 * 单个字段的输出策略。
 *
 * <p>后续 Jackson 字段脱敏模块会根据这个模型决定字段明文、脱敏、隐藏或拒绝。</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class FieldPolicy {

    @JsonProperty("field_name")
    private String fieldName;

    @JsonProperty("permission_type")
    private PermissionType permissionType = PermissionType.SHOW;

    @JsonProperty("mask_regex")
    private String maskRegex;

    /**
     * 策略生效场景，例如 DETAIL、EXPORT。
     *
     * <p>这里先保留字符串列表，不急着做枚举强约束，避免权限服务新增场景时 SDK 反序列化失败。</p>
     */
    @JsonProperty("scene")
    private List<String> scene = new ArrayList<>();

    public String getFieldName() {
        return fieldName;
    }

    public void setFieldName(String fieldName) {
        this.fieldName = fieldName;
    }

    public PermissionType getPermissionType() {
        return permissionType;
    }

    public void setPermissionType(PermissionType permissionType) {
        this.permissionType = permissionType;
    }

    public String getMaskRegex() {
        return maskRegex;
    }

    public void setMaskRegex(String maskRegex) {
        this.maskRegex = maskRegex;
    }

    public List<String> getScene() {
        return scene;
    }

    public void setScene(List<String> scene) {
        this.scene = scene == null ? new ArrayList<>() : scene;
    }
}

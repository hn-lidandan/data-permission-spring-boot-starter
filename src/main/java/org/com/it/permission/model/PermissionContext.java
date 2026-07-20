package org.com.it.permission.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import tools.jackson.databind.annotation.JsonDeserialize;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 权限服务返回并打平后的权限上下文。
 *
 * <p>这个类对应权限服务接口返回的 JSON 契约，字段名按接口里的 snake_case 做 Jackson 映射。
 * 它描述“当前用户能访问哪些部门、资产组、数据等级、资源、字段和导出范围”。SDK 各模块只读取这个模型，
 * 不在业务服务侧重新计算复杂授权关系。</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class PermissionContext {

    @JsonProperty("user_id")
    private String userId;

    @JsonProperty("tenant_id")
    private String tenantId;

    @JsonProperty("dept_scope")
    private List<String> deptScope = new ArrayList<>();

    @JsonProperty("asset_group_scope")
    private List<String> assetGroupScope = new ArrayList<>();

    @JsonProperty("data_level_scope")
    private List<String> dataLevelScope = new ArrayList<>();

    @JsonProperty("resource_object")
    private Map<String, List<String>> resourceObject = new LinkedHashMap<>();

    @JsonProperty("field_policies")
    private List<FieldPolicy> fieldPolicies = new ArrayList<>();

    @JsonProperty("export_policies")
    private List<ExportPolicy> exportPolicies = new ArrayList<>();

    @JsonProperty("deny_all")
    private boolean denyAll;

    @JsonProperty("version")
    private Long version;

    @JsonProperty("expires_at")
    @JsonDeserialize(using = FlexibleLocalDateTimeDeserializer.class)
    private LocalDateTime expiresAt;

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public List<String> getDeptScope() {
        return deptScope;
    }

    public void setDeptScope(List<String> deptScope) {
        this.deptScope = deptScope == null ? new ArrayList<>() : deptScope;
    }

    public List<String> getAssetGroupScope() {
        return assetGroupScope;
    }

    public void setAssetGroupScope(List<String> assetGroupScope) {
        this.assetGroupScope = assetGroupScope == null ? new ArrayList<>() : assetGroupScope;
    }

    public List<String> getDataLevelScope() {
        return dataLevelScope;
    }

    public void setDataLevelScope(List<String> dataLevelScope) {
        this.dataLevelScope = dataLevelScope == null ? new ArrayList<>() : dataLevelScope;
    }

    public Map<String, List<String>> getResourceObject() {
        return resourceObject;
    }

    public void setResourceObject(Map<String, List<String>> resourceObject) {
        this.resourceObject = resourceObject == null ? new LinkedHashMap<>() : resourceObject;
    }

    public List<FieldPolicy> getFieldPolicies() {
        return fieldPolicies;
    }

    public void setFieldPolicies(List<FieldPolicy> fieldPolicies) {
        this.fieldPolicies = fieldPolicies == null ? new ArrayList<>() : fieldPolicies;
    }

    public List<ExportPolicy> getExportPolicies() {
        return exportPolicies;
    }

    public void setExportPolicies(List<ExportPolicy> exportPolicies) {
        this.exportPolicies = exportPolicies == null ? new ArrayList<>() : exportPolicies;
    }

    public boolean isDenyAll() {
        return denyAll;
    }

    public void setDenyAll(boolean denyAll) {
        this.denyAll = denyAll;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }
}

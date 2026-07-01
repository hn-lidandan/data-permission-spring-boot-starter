package org.com.it.permission.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 单个资源的导出策略。
 *
 * <p>它对应权限上下文中的 export_policies。当前阶段先作为模型保存，后续导出 AOP 会读取这个策略，
 * 判断某个资源是否允许导出、最大导出条数是多少、是否需要审批。</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ExportPolicy {

    /**
     * 资源类型，例如 doris、postgre_sql。这里先用字符串接住服务端原值，后续执行层再统一归一化。
     */
    @JsonProperty("resource_type")
    private String resourceType;

    /**
     * 资源 ID，例如 security_log。
     */
    @JsonProperty("resource_id")
    private String resourceId;

    /**
     * 是否允许导出。
     *
     * <p>接口契约字段是 is_allowed；真实返回里可能同时带 allowed，这里用别名兼容。</p>
     */
    @JsonProperty("is_allowed")
    @JsonAlias("allowed")
    private boolean allowed;

    /**
     * 当前资源允许导出的最大行数。
     */
    @JsonProperty("max_rows")
    private int maxRows;

    /**
     * 是否需要审批。一期设计里先直接拒绝需要审批的导出，后续再接审批流。
     */
    @JsonProperty("need_approval")
    private boolean needApproval;

    public String getResourceType() {
        return resourceType;
    }

    public void setResourceType(String resourceType) {
        this.resourceType = resourceType;
    }

    public String getResourceId() {
        return resourceId;
    }

    public void setResourceId(String resourceId) {
        this.resourceId = resourceId;
    }

    public boolean isAllowed() {
        return allowed;
    }

    public void setAllowed(boolean allowed) {
        this.allowed = allowed;
    }

    public int getMaxRows() {
        return maxRows;
    }

    public void setMaxRows(int maxRows) {
        this.maxRows = maxRows;
    }

    public boolean isNeedApproval() {
        return needApproval;
    }

    public void setNeedApproval(boolean needApproval) {
        this.needApproval = needApproval;
    }
}

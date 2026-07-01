package org.com.it.permission.identity;

/**
 * 当前请求的身份信息。
 *
 * <p>它描述“谁在访问”，不是“有哪些权限”。权限范围由权限服务返回的 PermissionContext 表达。</p>
 */
public class PermissionIdentity {

    private String tenantId;

    private String userId;

    private String deptId;

    private String roleId;

    private String traceId;

    private String clientIp;

    private String clientApp;

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getDeptId() {
        return deptId;
    }

    public void setDeptId(String deptId) {
        this.deptId = deptId;
    }

    public String getRoleId() {
        return roleId;
    }

    public void setRoleId(String roleId) {
        this.roleId = roleId;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public String getClientIp() {
        return clientIp;
    }

    public void setClientIp(String clientIp) {
        this.clientIp = clientIp;
    }

    public String getClientApp() {
        return clientApp;
    }

    public void setClientApp(String clientApp) {
        this.clientApp = clientApp;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private final PermissionIdentity identity = new PermissionIdentity();

        public Builder tenantId(String tenantId) {
            identity.setTenantId(tenantId);
            return this;
        }

        public Builder userId(String userId) {
            identity.setUserId(userId);
            return this;
        }

        public Builder deptId(String deptId) {
            identity.setDeptId(deptId);
            return this;
        }

        public Builder roleId(String roleId) {
            identity.setRoleId(roleId);
            return this;
        }

        public Builder traceId(String traceId) {
            identity.setTraceId(traceId);
            return this;
        }

        public Builder clientIp(String clientIp) {
            identity.setClientIp(clientIp);
            return this;
        }

        public Builder clientApp(String clientApp) {
            identity.setClientApp(clientApp);
            return this;
        }

        public PermissionIdentity build() {
            return identity;
        }
    }
}

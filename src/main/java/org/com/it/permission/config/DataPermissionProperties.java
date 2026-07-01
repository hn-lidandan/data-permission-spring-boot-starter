package org.com.it.permission.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * SDK 配置属性。
 *
 * <p>绑定业务服务中的 {@code data-permission.*} 配置。这个类只描述配置结构，
 * 启动期校验放在自动装配类里，避免关闭 SDK 时也强制要求配置完整。</p>
 */
@ConfigurationProperties(prefix = "data-permission")
public class DataPermissionProperties {

    /**
     * SDK 总开关。默认关闭，避免业务服务引入依赖后立即改变运行行为。
     */
    private boolean enabled = false;

    /**
     * 权限服务地址。开启 SDK 后必填，后续 HTTP Client 会用它调用权限上下文接口。
     */
    private String serviceUrl;

    /**
     * 当前业务服务标识，用于组成缓存 key，也会进入审计事件。
     */
    private String clientApp = "default";

    private final CacheProperties cache = new CacheProperties();

    private final ClientProperties client = new ClientProperties();

    private final WebProperties web = new WebProperties();

    private final SqlProperties sql = new SqlProperties();

    private final FieldMaskProperties fieldMask = new FieldMaskProperties();

    private final ExportProperties export = new ExportProperties();

    private final KafkaProperties kafka = new KafkaProperties();

    private final HeadersProperties headers = new HeadersProperties();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getServiceUrl() {
        return serviceUrl;
    }

    public void setServiceUrl(String serviceUrl) {
        this.serviceUrl = serviceUrl;
    }

    public String getClientApp() {
        return clientApp;
    }

    public void setClientApp(String clientApp) {
        this.clientApp = clientApp;
    }

    public CacheProperties getCache() {
        return cache;
    }

    public ClientProperties getClient() {
        return client;
    }

    public WebProperties getWeb() {
        return web;
    }

    public SqlProperties getSql() {
        return sql;
    }

    public FieldMaskProperties getFieldMask() {
        return fieldMask;
    }

    public ExportProperties getExport() {
        return export;
    }

    public KafkaProperties getKafka() {
        return kafka;
    }

    public HeadersProperties getHeaders() {
        return headers;
    }

    public String requireClientApp() {
        return StringUtils.hasText(clientApp) ? clientApp : "default";
    }

    /**
     * 权限上下文本地缓存配置。
     */
    public static class CacheProperties {

        private long expireSeconds = 60;

        private long maximumSize = 10000;

        public long getExpireSeconds() {
            return expireSeconds;
        }

        public void setExpireSeconds(long expireSeconds) {
            this.expireSeconds = expireSeconds;
        }

        public long getMaximumSize() {
            return maximumSize;
        }

        public void setMaximumSize(long maximumSize) {
            this.maximumSize = maximumSize;
        }
    }

    /**
     * 权限服务 HTTP Client 配置。
     */
    public static class ClientProperties {

        /**
         * 连接权限服务的超时时间，单位毫秒。
         */
        private long connectTimeoutMillis = 3000;

        /**
         * 单次查询权限上下文的整体请求超时时间，单位毫秒。
         */
        private long requestTimeoutMillis = 5000;

        public long getConnectTimeoutMillis() {
            return connectTimeoutMillis;
        }

        public void setConnectTimeoutMillis(long connectTimeoutMillis) {
            this.connectTimeoutMillis = connectTimeoutMillis;
        }

        public long getRequestTimeoutMillis() {
            return requestTimeoutMillis;
        }

        public void setRequestTimeoutMillis(long requestTimeoutMillis) {
            this.requestTimeoutMillis = requestTimeoutMillis;
        }
    }

    public static class WebProperties {

        /**
         * Web 拦截器开关。默认开启，因为大多数业务服务需要在请求入口初始化上下文。
         */
        private boolean enabled = true;

        private String pathPattern = "/**";

        private final List<String> excludePathPatterns = new ArrayList<>();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getPathPattern() {
            return pathPattern;
        }

        public void setPathPattern(String pathPattern) {
            this.pathPattern = pathPattern;
        }

        public List<String> getExcludePathPatterns() {
            return excludePathPatterns;
        }
    }

    public static class SqlProperties {

        /**
         * SQL 改写开关。当前阶段先默认关闭，等 SQL 改写 MVP 完成后再建议业务服务打开。
         */
        private boolean enabled = false;

        private boolean failOnUnsupportedSql = true;

        private final Map<String, ResourceProperties> resources = new LinkedHashMap<>();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isFailOnUnsupportedSql() {
            return failOnUnsupportedSql;
        }

        public void setFailOnUnsupportedSql(boolean failOnUnsupportedSql) {
            this.failOnUnsupportedSql = failOnUnsupportedSql;
        }

        public Map<String, ResourceProperties> getResources() {
            return resources;
        }
    }

    public static class ResourceProperties {

        /**
         * 资源类型，例如 DORIS、POSTGRESQL。
         */
        private String resourceType;

        /**
         * 权限服务中的资源 ID，例如 security_log。
         */
        private String resourceId;

        /**
         * 当前资源对应的物理表名列表。
         */
        private final List<String> tableNames = new ArrayList<>();

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

        public List<String> getTableNames() {
            return tableNames;
        }
    }

    public static class FieldMaskProperties {

        /**
         * 字段脱敏开关。当前阶段只预留配置，不注册脱敏模块。
         */
        private boolean enabled = false;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    public static class ExportProperties {

        /**
         * 导出控制开关。当前阶段只预留配置，不注册导出 AOP。
         */
        private boolean enabled = false;

        private int maxRows = 10000;

        private boolean forceMask = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getMaxRows() {
            return maxRows;
        }

        public void setMaxRows(int maxRows) {
            this.maxRows = maxRows;
        }

        public boolean isForceMask() {
            return forceMask;
        }

        public void setForceMask(boolean forceMask) {
            this.forceMask = forceMask;
        }
    }

    public static class KafkaProperties {

        /**
         * Kafka 开关。当前阶段只预留配置，不启动消费者或生产者。
         */
        private boolean enabled = false;

        private String invalidateTopic = "auth-context-invalidate";

        private String auditTopic = "data-permission-audit-topic";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getInvalidateTopic() {
            return invalidateTopic;
        }

        public void setInvalidateTopic(String invalidateTopic) {
            this.invalidateTopic = invalidateTopic;
        }

        public String getAuditTopic() {
            return auditTopic;
        }

        public void setAuditTopic(String auditTopic) {
            this.auditTopic = auditTopic;
        }
    }

    public static class HeadersProperties {

        /**
         * 默认从这些 Header 读取身份信息。生产环境应保证这些 Header 来自可信认证链路。
         */
        private String tenantId = "X-Tenant-Id";

        private String userId = "X-User-Id";

        private String deptId = "X-Dept-Id";

        private String roleId = "X-Role-Id";

        private String traceId = "X-Trace-Id";

        private String clientApp = "X-Client-App";

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

        public String getClientApp() {
            return clientApp;
        }

        public void setClientApp(String clientApp) {
            this.clientApp = clientApp;
        }
    }
}

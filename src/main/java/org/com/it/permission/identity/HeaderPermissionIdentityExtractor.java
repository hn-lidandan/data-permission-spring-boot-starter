package org.com.it.permission.identity;

import jakarta.servlet.http.HttpServletRequest;
import org.com.it.permission.config.DataPermissionProperties;
import org.com.it.permission.exception.PermissionDeniedException;
import org.springframework.util.StringUtils;

/**
 * 从 HTTP Header 提取当前请求身份。
 *
 * <p>这是一期默认实现，方便先跑通 SDK 链路。正式生产接入时，推荐业务服务提供自己的
 * {@link PermissionIdentityExtractor} Bean，从认证上下文或网关注入的可信信息中读取身份。</p>
 */
public class HeaderPermissionIdentityExtractor implements PermissionIdentityExtractor {

    private final DataPermissionProperties properties;

    public HeaderPermissionIdentityExtractor(DataPermissionProperties properties) {
        this.properties = properties;
    }

    @Override
    public PermissionIdentity extract(HttpServletRequest request) {
        DataPermissionProperties.HeadersProperties headers = properties.getHeaders();
        // 权限服务上下文接口要求 tenant_id、user_id、dept_id、role_id 四个字段必填，缺一律拒绝。
        String tenantId = requiredHeader(request, headers.getTenantId(), "tenant_id");
        String userId = requiredHeader(request, headers.getUserId(), "user_id");
        String deptId = requiredHeader(request, headers.getDeptId(), "dept_id");
        String roleId = requiredHeader(request, headers.getRoleId(), "role_id");
        String clientApp = firstText(request.getHeader(headers.getClientApp()), properties.requireClientApp());

        return PermissionIdentity.builder()
                .tenantId(tenantId)
                .userId(userId)
                .deptId(deptId)
                .roleId(roleId)
                .traceId(request.getHeader(headers.getTraceId()))
                .clientIp(resolveClientIp(request))
                .clientApp(clientApp)
                .build();
    }

    private String requiredHeader(HttpServletRequest request, String headerName, String fieldName) {
        String value = request.getHeader(headerName);
        if (!StringUtils.hasText(value)) {
            throw new PermissionDeniedException("Missing required permission identity: " + fieldName);
        }
        return value;
    }

    private String firstText(String first, String fallback) {
        return StringUtils.hasText(first) ? first : fallback;
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwardedFor)) {
            // X-Forwarded-For 可能是多级代理链路，这里取最前面的原始客户端 IP。
            int commaIndex = forwardedFor.indexOf(',');
            return commaIndex >= 0 ? forwardedFor.substring(0, commaIndex).trim() : forwardedFor;
        }
        return request.getRemoteAddr();
    }
}

package org.com.it.permission.identity;

import org.com.it.permission.config.DataPermissionProperties;
import org.com.it.permission.exception.PermissionDeniedException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Header 身份提取器测试。
 *
 * <p>一期默认从 HTTP Header 读取 tenantId、userId、deptId、roleId 等身份信息。这里验证完整身份能成功提取，
 * 缺少权限服务上下文接口必填身份字段时会默认拒绝。</p>
 */
class HeaderPermissionIdentityExtractorTests {

    @Test
    void shouldExtractIdentityFromHeaders() {
        // 完整 Header 场景：必填身份、辅助身份、traceId、客户端 IP 都应该能读取出来。
        DataPermissionProperties properties = new DataPermissionProperties();
        properties.setClientApp("log-service");
        HeaderPermissionIdentityExtractor extractor = new HeaderPermissionIdentityExtractor(properties);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Tenant-Id", "t001");
        request.addHeader("X-User-Id", "u001");
        request.addHeader("X-Dept-Id", "d001");
        request.addHeader("X-Role-Id", "r001");
        request.addHeader("X-Trace-Id", "trace-001");
        request.addHeader("X-Forwarded-For", "10.0.0.1, 10.0.0.2");

        PermissionIdentity identity = extractor.extract(request);

        assertThat(identity.getTenantId()).isEqualTo("t001");
        assertThat(identity.getUserId()).isEqualTo("u001");
        assertThat(identity.getDeptId()).isEqualTo("d001");
        assertThat(identity.getRoleId()).isEqualTo("r001");
        assertThat(identity.getTraceId()).isEqualTo("trace-001");
        assertThat(identity.getClientIp()).isEqualTo("10.0.0.1");
        assertThat(identity.getClientApp()).isEqualTo("log-service");
    }

    @Test
    void shouldDenyWhenTenantIdMissing() {
        // tenantId 是权限上下文查询的必需字段，缺失时直接拒绝。
        HeaderPermissionIdentityExtractor extractor = new HeaderPermissionIdentityExtractor(new DataPermissionProperties());
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-User-Id", "u001");
        request.addHeader("X-Dept-Id", "d001");
        request.addHeader("X-Role-Id", "r001");

        assertThatThrownBy(() -> extractor.extract(request))
                .isInstanceOf(PermissionDeniedException.class)
                .hasMessageContaining("tenant_id");
    }

    @Test
    void shouldDenyWhenUserIdMissing() {
        // userId 是权限上下文查询的必需字段，缺失时直接拒绝。
        HeaderPermissionIdentityExtractor extractor = new HeaderPermissionIdentityExtractor(new DataPermissionProperties());
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Tenant-Id", "t001");
        request.addHeader("X-Dept-Id", "d001");
        request.addHeader("X-Role-Id", "r001");

        assertThatThrownBy(() -> extractor.extract(request))
                .isInstanceOf(PermissionDeniedException.class)
                .hasMessageContaining("user_id");
    }

    @Test
    void shouldDenyWhenDeptIdMissing() {
        // deptId 是权限上下文查询接口的必需字段，缺失时直接拒绝。
        HeaderPermissionIdentityExtractor extractor = new HeaderPermissionIdentityExtractor(new DataPermissionProperties());
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Tenant-Id", "t001");
        request.addHeader("X-User-Id", "u001");
        request.addHeader("X-Role-Id", "r001");

        assertThatThrownBy(() -> extractor.extract(request))
                .isInstanceOf(PermissionDeniedException.class)
                .hasMessageContaining("dept_id");
    }

    @Test
    void shouldDenyWhenRoleIdMissing() {
        // roleId 是权限上下文查询接口的必需字段，缺失时直接拒绝。
        HeaderPermissionIdentityExtractor extractor = new HeaderPermissionIdentityExtractor(new DataPermissionProperties());
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Tenant-Id", "t001");
        request.addHeader("X-User-Id", "u001");
        request.addHeader("X-Dept-Id", "d001");

        assertThatThrownBy(() -> extractor.extract(request))
                .isInstanceOf(PermissionDeniedException.class)
                .hasMessageContaining("role_id");
    }
}

package org.com.it.permission.identity;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 请求身份提取接口。
 *
 * <p>业务服务可以声明自己的 Bean 覆盖默认 Header 实现，例如从 Spring Security、
 * 网关认证结果或公司统一登录上下文中读取 tenantId/userId。</p>
 */
public interface PermissionIdentityExtractor {

    PermissionIdentity extract(HttpServletRequest request);
}

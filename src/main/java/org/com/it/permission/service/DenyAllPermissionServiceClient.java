package org.com.it.permission.service;

import org.com.it.permission.identity.PermissionIdentity;
import org.com.it.permission.model.PermissionContext;

/**
 * 默认拒绝的权限服务客户端。
 *
 * <p>当前 SDK 主链路已经使用 {@link HttpPermissionServiceClient} 调用权限服务。
 * 这个实现保留给单元测试或极端兜底场景使用：只要被调用，就返回 denyAll，避免误放行。</p>
 */
public class DenyAllPermissionServiceClient implements PermissionServiceClient {

    @Override
    public PermissionContext queryContext(PermissionIdentity identity) {
        PermissionContext context = new PermissionContext();
        context.setTenantId(identity.getTenantId());
        context.setUserId(identity.getUserId());
        context.setDenyAll(true);
        return context;
    }
}

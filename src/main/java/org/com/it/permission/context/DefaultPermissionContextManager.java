package org.com.it.permission.context;

import org.com.it.permission.cache.PermissionContextCache;
import org.com.it.permission.config.DataPermissionProperties;
import org.com.it.permission.exception.PermissionDeniedException;
import org.com.it.permission.identity.PermissionIdentity;
import org.com.it.permission.model.PermissionContext;
import org.com.it.permission.service.PermissionServiceClient;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/**
 * 默认权限上下文管理器。
 *
 * <p>请求进入后，Web 拦截器会先得到 PermissionIdentity，然后调用这里获取 PermissionContext。
 * 获取顺序是：先查本地缓存，未命中再调用权限服务客户端，成功后写入本地缓存。</p>
 */
public class DefaultPermissionContextManager implements PermissionContextManager {

    private final PermissionContextCache cache;

    private final PermissionServiceClient client;

    private final DataPermissionProperties properties;

    public DefaultPermissionContextManager(PermissionContextCache cache,
                                           PermissionServiceClient client,
                                           DataPermissionProperties properties) {
        this.cache = cache;
        this.client = client;
        this.properties = properties;
    }

    @Override
    public PermissionContext getContext(PermissionIdentity identity) {
        String key = buildCacheKey(identity);
        return cache.get(key)
                .orElseGet(() -> loadAndCache(key, identity));
    }

    @Override
    public String buildCacheKey(PermissionIdentity identity) {
        requireText(identity.getTenantId(), "tenant_id");
        requireText(identity.getUserId(), "user_id");
        requireText(identity.getDeptId(), "dept_id");
        requireText(identity.getRoleId(), "role_id");

        // clientApp、部门和角色都参与缓存 key，避免同一用户在不同权限身份下复用旧上下文。
        String clientApp = StringUtils.hasText(identity.getClientApp())
                ? identity.getClientApp()
                : properties.requireClientApp();
        return clientApp
                + ":" + identity.getTenantId()
                + ":" + identity.getUserId()
                + ":" + identity.getDeptId()
                + ":" + identity.getRoleId();
    }

    @Override
    public void evict(PermissionIdentity identity) {
        cache.evict(buildCacheKey(identity));
    }

    @Override
    public void clear() {
        cache.clear();
    }

    private PermissionContext loadAndCache(String key, PermissionIdentity identity) {
        PermissionContext context = client.queryContext(identity);
        // 默认拒绝：权限服务没返回上下文、明确 denyAll、或上下文已经过期，都不允许继续访问。
        if (context == null || context.isDenyAll() || isExpired(context)) {
            throw new PermissionDeniedException("Permission context denied");
        }
        cache.put(key, context);
        return context;
    }

    private boolean isExpired(PermissionContext context) {
        return context.getExpiresAt() != null && context.getExpiresAt().isBefore(LocalDateTime.now());
    }

    private void requireText(String value, String fieldName) {
        if (!StringUtils.hasText(value)) {
            throw new PermissionDeniedException("Missing required permission identity: " + fieldName);
        }
    }
}

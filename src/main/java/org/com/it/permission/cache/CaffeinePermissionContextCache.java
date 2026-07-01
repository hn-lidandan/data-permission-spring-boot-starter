package org.com.it.permission.cache;

import com.github.benmanes.caffeine.cache.Cache;
import org.com.it.permission.model.PermissionContext;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 基于 Caffeine 的本地权限上下文缓存。
 *
 * <p>一期只使用 JVM 本地缓存，不引入 Redis。缓存里的上下文如果带了 expiresAt，
 * 即使 Caffeine 还没触发过期，也会在读取时做一次业务过期判断。</p>
 */
public class CaffeinePermissionContextCache implements PermissionContextCache {

    private final Cache<String, PermissionContext> cache;

    public CaffeinePermissionContextCache(Cache<String, PermissionContext> cache) {
        this.cache = cache;
    }

    @Override
    public Optional<PermissionContext> get(String key) {
        PermissionContext context = cache.getIfPresent(key);
        if (context == null) {
            return Optional.empty();
        }
        // 权限服务返回的 expiresAt 是业务有效期，优先级高于本地缓存 TTL。
        if (context.getExpiresAt() != null && context.getExpiresAt().isBefore(LocalDateTime.now())) {
            evict(key);
            return Optional.empty();
        }
        return Optional.of(context);
    }

    @Override
    public void put(String key, PermissionContext context) {
        cache.put(key, context);
    }

    @Override
    public void evict(String key) {
        cache.invalidate(key);
    }

    @Override
    public void clear() {
        cache.invalidateAll();
    }
}

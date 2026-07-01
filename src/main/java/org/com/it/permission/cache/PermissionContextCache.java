package org.com.it.permission.cache;

import org.com.it.permission.model.PermissionContext;

import java.util.Optional;

/**
 * 权限上下文缓存抽象。
 *
 * <p>SQL 改写、字段脱敏、导出控制等模块不直接依赖 Caffeine，只通过这个接口获取上下文缓存能力。</p>
 */
public interface PermissionContextCache {

    Optional<PermissionContext> get(String key);

    void put(String key, PermissionContext context);

    void evict(String key);

    void clear();
}

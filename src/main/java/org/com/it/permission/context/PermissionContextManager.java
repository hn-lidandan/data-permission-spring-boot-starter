package org.com.it.permission.context;

import org.com.it.permission.identity.PermissionIdentity;
import org.com.it.permission.model.PermissionContext;

/**
 * 权限上下文管理接口。
 *
 * <p>后续 SQL 改写、字段脱敏、导出控制都不应该自己调用权限服务，而是通过请求线程中的
 * {@link PermissionContextHolder} 读取上下文；只有请求入口负责调用这个 Manager 初始化上下文。</p>
 */
public interface PermissionContextManager {

    PermissionContext getContext(PermissionIdentity identity);

    String buildCacheKey(PermissionIdentity identity);

    void evict(PermissionIdentity identity);

    void clear();
}

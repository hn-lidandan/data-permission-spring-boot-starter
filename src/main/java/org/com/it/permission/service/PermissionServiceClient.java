package org.com.it.permission.service;

import org.com.it.permission.identity.PermissionIdentity;
import org.com.it.permission.model.PermissionContext;

/**
 * 权限服务客户端接口。
 *
 * <p>默认实现由 SDK 内置的 {@link HttpPermissionServiceClient} 提供，会调用业务服务配置的
 * {@code data-permission.service-url}。ContextManager 只依赖这个接口，不关心底层 HTTP 细节。</p>
 */
public interface PermissionServiceClient {

    PermissionContext queryContext(PermissionIdentity identity);
}

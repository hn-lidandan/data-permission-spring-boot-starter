package org.com.it.permission.web;

import org.com.it.permission.context.PermissionContextHolder;
import org.com.it.permission.context.PermissionContextManager;
import org.com.it.permission.exception.PermissionDeniedException;
import org.com.it.permission.identity.PermissionIdentity;
import org.com.it.permission.model.PermissionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Web 拦截器测试。
 *
 * <p>这里验证请求入口的两个关键行为：正常请求会把权限上下文绑定到 ThreadLocal；
 * 权限异常会返回 403，并清理掉可能残留的 ThreadLocal。</p>
 */
class DataPermissionWebInterceptorTests {

    @AfterEach
    void tearDown() {
        // 防止某个测试失败后 ThreadLocal 泄漏到下一个测试。
        PermissionContextHolder.clear();
    }

    @Test
    void shouldBindContextOnPreHandleAndClearAfterCompletion() throws Exception {
        // preHandle 成功时绑定上下文；afterCompletion 不管请求结果如何都要清理上下文。
        PermissionContext expectedContext = new PermissionContext();
        expectedContext.setTenantId("t001");
        expectedContext.setUserId("u001");
        DataPermissionWebInterceptor interceptor = new DataPermissionWebInterceptor(
                request -> PermissionIdentity.builder()
                        .tenantId("t001")
                        .userId("u001")
                        .clientApp("log-service")
                        .build(),
                new StaticPermissionContextManager(expectedContext)
        );

        boolean result = interceptor.preHandle(new MockHttpServletRequest(), new MockHttpServletResponse(), new Object());

        assertThat(result).isTrue();
        assertThat(PermissionContextHolder.get()).containsSame(expectedContext);

        interceptor.afterCompletion(new MockHttpServletRequest(), new MockHttpServletResponse(), new Object(), null);

        assertThat(PermissionContextHolder.get()).isEmpty();
    }

    @Test
    void shouldReturnForbiddenAndClearContextWhenDenied() throws Exception {
        // 模拟当前线程已经有旧上下文，权限拒绝时必须返回 403 且清理旧上下文。
        PermissionContext staleContext = new PermissionContext();
        PermissionContextHolder.set(staleContext);
        DataPermissionWebInterceptor interceptor = new DataPermissionWebInterceptor(
                request -> {
                    throw new PermissionDeniedException("Missing required permission identity: tenant_id");
                },
                new StaticPermissionContextManager(new PermissionContext())
        );
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean result = interceptor.preHandle(new MockHttpServletRequest(), response, new Object());

        assertThat(result).isFalse();
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(PermissionContextHolder.get()).isEmpty();
    }

    /**
     * 测试用上下文管理器：固定返回传入的 PermissionContext，避免测试依赖真实缓存或权限服务。
     */
    static class StaticPermissionContextManager implements PermissionContextManager {

        private final PermissionContext context;

        StaticPermissionContextManager(PermissionContext context) {
            this.context = context;
        }

        @Override
        public PermissionContext getContext(PermissionIdentity identity) {
            return context;
        }

        @Override
        public String buildCacheKey(PermissionIdentity identity) {
            return "key";
        }

        @Override
        public void evict(PermissionIdentity identity) {
        }

        @Override
        public void clear() {
        }
    }
}

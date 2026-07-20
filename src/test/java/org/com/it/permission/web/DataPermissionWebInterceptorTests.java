package org.com.it.permission.web;

import org.com.it.permission.context.PermissionContextHolder;
import org.com.it.permission.context.PermissionContextManager;
import org.com.it.permission.context.DataPermissionSceneHolder;
import org.com.it.permission.exception.PermissionDeniedException;
import org.com.it.permission.identity.PermissionIdentity;
import org.com.it.permission.model.PermissionContext;
import org.com.it.permission.scene.PermissionScene;
import org.com.it.permission.scene.PermissionScenes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;

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
        DataPermissionSceneHolder.clear();
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

        DataPermissionSceneHolder.set("DETAIL");
        interceptor.afterCompletion(new MockHttpServletRequest(), new MockHttpServletResponse(), new Object(), null);

        assertThat(PermissionContextHolder.get()).isEmpty();
        assertThat(DataPermissionSceneHolder.get()).isEmpty();
    }

    @Test
    void shouldReturnForbiddenAndClearContextWhenDenied() throws Exception {
        // 模拟当前线程已经有旧上下文，权限拒绝时必须返回 403 且清理旧上下文。
        PermissionContext staleContext = new PermissionContext();
        PermissionContextHolder.set(staleContext);
        DataPermissionSceneHolder.set("EXPORT");
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
        assertThat(DataPermissionSceneHolder.get()).isEmpty();
    }

    @Test
    void shouldBindSceneFromHandlerMethodAnnotation() throws Exception {
        // 方法上有 @PermissionScene 时，preHandle 要把场景绑定到当前线程，
        // 场景生命周期覆盖响应体序列化阶段，直到 afterCompletion 才清理。
        DataPermissionWebInterceptor interceptor = interceptor();
        HandlerMethod handler = new HandlerMethod(new AnnotatedEndpoint(),
                AnnotatedEndpoint.class.getMethod("queryList"));

        boolean result = interceptor.preHandle(new MockHttpServletRequest(), new MockHttpServletResponse(), handler);

        assertThat(result).isTrue();
        // controller 返回后（切面已出栈）场景必须仍然可读，这是字段脱敏能覆盖序列化阶段的关键。
        assertThat(DataPermissionSceneHolder.get()).contains(PermissionScenes.QUERY);

        interceptor.afterCompletion(new MockHttpServletRequest(), new MockHttpServletResponse(), handler, null);
        assertThat(DataPermissionSceneHolder.get()).isEmpty();
    }

    @Test
    void shouldBindSceneFromControllerClassAnnotationWhenMethodHasNone() throws Exception {
        DataPermissionWebInterceptor interceptor = interceptor();
        HandlerMethod handler = new HandlerMethod(new ClassAnnotatedEndpoint(),
                ClassAnnotatedEndpoint.class.getMethod("export"));

        interceptor.preHandle(new MockHttpServletRequest(), new MockHttpServletResponse(), handler);

        assertThat(DataPermissionSceneHolder.get()).contains(PermissionScenes.EXPORT);
    }

    @Test
    void shouldNotBindSceneWhenHandlerHasNoAnnotation() throws Exception {
        DataPermissionWebInterceptor interceptor = interceptor();
        HandlerMethod handler = new HandlerMethod(new PlainEndpoint(),
                PlainEndpoint.class.getMethod("plain"));

        interceptor.preHandle(new MockHttpServletRequest(), new MockHttpServletResponse(), handler);

        assertThat(DataPermissionSceneHolder.get()).isEmpty();
    }

    private DataPermissionWebInterceptor interceptor() {
        return new DataPermissionWebInterceptor(
                request -> PermissionIdentity.builder()
                        .tenantId("t001")
                        .userId("u001")
                        .clientApp("log-service")
                        .build(),
                new StaticPermissionContextManager(new PermissionContext())
        );
    }

    static class AnnotatedEndpoint {

        @PermissionScene(PermissionScenes.QUERY)
        public String queryList() {
            return "ok";
        }
    }

    @PermissionScene(PermissionScenes.EXPORT)
    static class ClassAnnotatedEndpoint {

        public String export() {
            return "ok";
        }
    }

    static class PlainEndpoint {

        public String plain() {
            return "ok";
        }
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

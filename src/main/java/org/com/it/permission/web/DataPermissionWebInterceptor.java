package org.com.it.permission.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.com.it.permission.context.DataPermissionSceneHolder;
import org.com.it.permission.context.PermissionContextHolder;
import org.com.it.permission.context.PermissionContextManager;
import org.com.it.permission.exception.DataPermissionException;
import org.com.it.permission.identity.PermissionIdentity;
import org.com.it.permission.identity.PermissionIdentityExtractor;
import org.com.it.permission.model.PermissionContext;
import org.com.it.permission.scene.PermissionScene;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;

/**
 * SDK 的 Web 请求入口拦截器。
 *
 * <p>它负责在 Controller 执行前完成四件事：提取身份、获取权限上下文、绑定 ThreadLocal、
 * 解析并绑定 {@code @PermissionScene} 场景。场景必须在这里绑定而不是只靠切面：
 * {@code @ResponseBody} 返回值的 JSON 序列化发生在 Controller 方法返回之后，切面的
 * finally 已经把场景清掉了，字段脱敏就会失效。拦截器绑定的场景生命周期覆盖整个请求，
 * 包含响应体序列化阶段，请求结束后在 afterCompletion 统一清理。</p>
 */
public class DataPermissionWebInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(DataPermissionWebInterceptor.class);

    private final PermissionIdentityExtractor identityExtractor;

    private final PermissionContextManager contextManager;

    /**
     * 创建 Web 请求入口拦截器。
     */
    public DataPermissionWebInterceptor(PermissionIdentityExtractor identityExtractor,
                                        PermissionContextManager contextManager) {
        this.identityExtractor = identityExtractor;
        this.contextManager = contextManager;
    }

    /**
     * Controller 执行前提取身份、获取权限上下文并绑定到当前线程，同时绑定处理方法上声明的场景。
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
        try {
            PermissionIdentity identity = identityExtractor.extract(request);
            PermissionContext context = contextManager.getContext(identity);
            PermissionContextHolder.set(context);
            bindScene(handler);
            return true;
        } catch (DataPermissionException ex) {
            // todo  待处理
            // 权限相关错误统一按 403 处理，避免没有权限时落到业务 Controller 或变成 500。
            PermissionContextHolder.clear();
            DataPermissionSceneHolder.clear();
            response.sendError(HttpServletResponse.SC_FORBIDDEN, ex.getMessage());
            return false;
        }
    }

    /**
     * 请求结束时清理权限上下文和场景上下文。
     */
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        // 不管请求成功、异常还是被业务拦截，都要清理 ThreadLocal。
        PermissionContextHolder.clear();
        DataPermissionSceneHolder.clear();
    }

    /**
     * 从处理方法解析 @PermissionScene 并绑定到当前线程，方法注解优先于类注解。
     */
    private void bindScene(Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return;
        }
        PermissionScene scene = AnnotatedElementUtils.findMergedAnnotation(handlerMethod.getMethod(), PermissionScene.class);
        if (scene == null) {
            scene = AnnotatedElementUtils.findMergedAnnotation(handlerMethod.getBeanType(), PermissionScene.class);
        }
        if (scene == null) {
            return;
        }

        String sceneValue = scene.value();
        if (!StringUtils.hasText(sceneValue)) {
            throw new IllegalStateException("@PermissionScene value must not be empty on "
                    + handlerMethod.getMethod());
        }
        DataPermissionSceneHolder.set(sceneValue);
        log.debug("[data-permission] scene [{}] bound for request handler {} (interceptor, covers response serialization)",
                sceneValue, handlerMethod);
    }
}

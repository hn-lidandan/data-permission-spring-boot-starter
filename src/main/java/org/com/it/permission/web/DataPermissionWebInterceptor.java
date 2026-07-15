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
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;

/**
 * SDK 的 Web 请求入口拦截器。
 *
 * <p>它负责在 Controller 执行前完成三件事：提取身份、获取权限上下文、绑定 ThreadLocal。
 * 请求结束后必须清理 ThreadLocal，避免线程池复用造成上下文串扰。</p>
 */
public class DataPermissionWebInterceptor implements HandlerInterceptor {

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
     * Controller 执行前提取身份、获取权限上下文并绑定到当前线程。
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
        try {
            PermissionIdentity identity = identityExtractor.extract(request);
            PermissionContext context = contextManager.getContext(identity);
            PermissionContextHolder.set(context);
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
}

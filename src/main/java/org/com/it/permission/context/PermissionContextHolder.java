package org.com.it.permission.context;

import org.com.it.permission.exception.PermissionContextMissingException;
import org.com.it.permission.model.PermissionContext;

import java.util.Optional;

/**
 * 当前请求线程的权限上下文持有器。
 *
 * <p>一期使用 ThreadLocal，是因为 Spring MVC 的一次请求通常在同一个工作线程内完成。
 * Web 拦截器必须在请求结束时调用 clear，避免线程池复用导致不同请求串上下文。</p>
 */
public final class PermissionContextHolder {

    private static final ThreadLocal<PermissionContext> CONTEXT = new ThreadLocal<>();

    private PermissionContextHolder() {
    }

    public static void set(PermissionContext context) {
        CONTEXT.set(context);
    }

    public static Optional<PermissionContext> get() {
        return Optional.ofNullable(CONTEXT.get());
    }

    public static PermissionContext require() {
        PermissionContext context = CONTEXT.get();
        if (context == null) {
            throw new PermissionContextMissingException("Permission context is missing");
        }
        return context;
    }

    public static void clear() {
        CONTEXT.remove();
    }
}

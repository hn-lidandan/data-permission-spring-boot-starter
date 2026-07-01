package org.com.it.permission.exception;

/**
 * 当前线程没有权限上下文时抛出。
 *
 * <p>通常说明请求入口没有经过 SDK Web 拦截器，或者请求结束后仍在访问 ThreadLocal。</p>
 */
public class PermissionContextMissingException extends DataPermissionException {

    public PermissionContextMissingException(String message) {
        super(message);
    }
}

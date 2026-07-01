package org.com.it.permission.exception;

/**
 * SDK 内部权限异常基类。
 *
 * <p>Web 拦截器会捕获这个类型并返回 403。</p>
 */
public class DataPermissionException extends RuntimeException {

    public DataPermissionException(String message) {
        super(message);
    }

    public DataPermissionException(String message, Throwable cause) {
        super(message, cause);
    }
}

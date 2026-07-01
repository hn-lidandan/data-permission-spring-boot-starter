package org.com.it.permission.exception;

/**
 * 权限拒绝异常。
 *
 * <p>包括缺少身份、权限服务返回 denyAll、权限上下文过期等默认拒绝场景。</p>
 */
public class PermissionDeniedException extends DataPermissionException {

    public PermissionDeniedException(String message) {
        super(message);
    }

    public PermissionDeniedException(String message, Throwable cause) {
        super(message, cause);
    }
}

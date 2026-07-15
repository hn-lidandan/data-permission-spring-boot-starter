package org.com.it.permission.exception;

/**
 * 命中权限资源但 SQL 结构不支持安全改写时抛出。
 */
public class UnsupportedPermissionSqlException extends PermissionDeniedException {

    /**
     * 创建 SQL 不支持异常。
     */
    public UnsupportedPermissionSqlException(String message) {
        super(message);
    }
}

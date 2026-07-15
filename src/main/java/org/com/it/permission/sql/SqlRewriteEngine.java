package org.com.it.permission.sql;

import org.com.it.permission.model.PermissionContext;

/**
 * SQL 行级权限改写引擎。
 */
public interface SqlRewriteEngine {

    /**
     * 根据权限上下文和场景改写 SQL。
     */
    SqlRewriteResult rewrite(String sql, PermissionContext context, String scene);
}

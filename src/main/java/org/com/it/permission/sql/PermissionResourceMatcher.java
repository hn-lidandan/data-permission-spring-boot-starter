package org.com.it.permission.sql;

import org.com.it.permission.model.PermissionContext;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 根据 PermissionContext.resource_object 判断 SQL 表是否需要权限过滤。
 */
public class PermissionResourceMatcher {

    /**
     * 判断表名是否存在于权限上下文的 resource_object 中。
     */
    public boolean matches(PermissionContext context, String tableName) {
        if (context == null || !StringUtils.hasText(tableName)) {
            return false;
        }
        Map<String, List<String>> resourceObject = context.getResourceObject();
        if (resourceObject == null || resourceObject.isEmpty()) {
            return false;
        }

        String normalizedTableName = normalize(tableName);
        // resource_object 按资源类型分组，例如 doris/postgre_sql；SQL 过滤只关心里面的 resource_id 是否等于表名。
        return resourceObject.values().stream()
                .filter(resources -> resources != null && !resources.isEmpty())
                .flatMap(List::stream)
                .filter(StringUtils::hasText)
                .map(PermissionResourceMatcher::normalize)
                .anyMatch(normalizedTableName::equals);
    }

    /**
     * 归一化 SQL 标识符，便于和 resource_id 做比较。
     */
    static String normalize(String identifier) {
        String value = identifier.trim();
        // 兼容 "table"、`table`、[table] 这类常见引用符号，比较时统一忽略大小写。
        if ((value.startsWith("\"") && value.endsWith("\""))
                || (value.startsWith("`") && value.endsWith("`"))
                || (value.startsWith("[") && value.endsWith("]"))) {
            value = value.substring(1, value.length() - 1);
        }
        return value.toLowerCase(Locale.ROOT);
    }
}

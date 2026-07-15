package org.com.it.permission.sql;

/**
 * SDK 注入到 SQL 中的权限参数。
 */
public class PermissionSqlParameter {

    /** 写入 MyBatis additionalParameters 时使用的参数名。 */
    private final String name;

    /** 参数真实值，例如 tenant_id 或某个 dept_id。 */
    private final Object value;

    /** MyBatis ParameterMapping 需要的 Java 类型。 */
    private final Class<?> javaType;

    /**
     * 创建一个 SDK 注入参数。
     */
    public PermissionSqlParameter(String name, Object value, Class<?> javaType) {
        this.name = name;
        this.value = value;
        this.javaType = javaType;
    }

    /**
     * 返回参数名。
     */
    public String getName() {
        return name;
    }

    /**
     * 返回参数值。
     */
    public Object getValue() {
        return value;
    }

    /**
     * 返回参数 Java 类型。
     */
    public Class<?> getJavaType() {
        return javaType;
    }
}

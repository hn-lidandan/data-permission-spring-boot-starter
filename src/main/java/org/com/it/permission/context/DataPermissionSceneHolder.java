package org.com.it.permission.context;

import org.springframework.util.StringUtils;

import java.util.Optional;

/**
 * 当前请求线程的数据权限场景持有器。
 *
 * <p>SQL 过滤、字段脱敏、导出控制都通过这个 Holder 读取统一场景。默认场景是 QUERY。</p>
 */
public final class DataPermissionSceneHolder {

    public static final String DEFAULT_SCENE = "QUERY";

    private static final ThreadLocal<String> SCENE = new ThreadLocal<>();

    /**
     * 工具类不允许实例化。
     */
    private DataPermissionSceneHolder() {
    }

    /**
     * 设置当前请求的数据权限场景。
     */
    public static void set(String scene) {
        // 场景统一转成大写，后续字段脱敏、导出控制做匹配时不用处理大小写差异。
        SCENE.set(StringUtils.hasText(scene) ? scene.trim().toUpperCase() : DEFAULT_SCENE);
    }

    /**
     * 获取当前线程中显式设置的场景。
     */
    public static Optional<String> get() {
        return Optional.ofNullable(SCENE.get());
    }

    /**
     * 获取当前场景；未设置时返回默认 QUERY。
     */
    public static String current() {
        // 没有显式声明场景时，普通请求按 QUERY 处理。
        return get().orElse(DEFAULT_SCENE);
    }

    /**
     * 清理当前线程场景，避免线程池复用导致场景串扰。
     */
    public static void clear() {
        SCENE.remove();
    }
}

package org.com.it.permission.context;

import org.com.it.permission.exception.PermissionContextMissingException;
import org.com.it.permission.model.PermissionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ThreadLocal 权限上下文测试。
 *
 * <p>ContextHolder 是 SQL 改写、字段脱敏、导出控制读取当前请求权限的统一入口，
 * 所以必须验证 set、get、require 和 clear 的行为。</p>
 */
class PermissionContextHolderTests {

    @AfterEach
    void tearDown() {
        // 测试结束后主动清理，避免 ThreadLocal 影响下一个测试。
        PermissionContextHolder.clear();
    }

    @Test
    void shouldSetGetRequireAndClearContext() {
        // 设置上下文后应该能读取；清理后 get 为空，require 应该抛出明确异常。
        PermissionContext context = new PermissionContext();
        context.setTenantId("t001");
        context.setUserId("u001");

        PermissionContextHolder.set(context);

        assertThat(PermissionContextHolder.get()).containsSame(context);
        assertThat(PermissionContextHolder.require()).isSameAs(context);

        PermissionContextHolder.clear();

        assertThat(PermissionContextHolder.get()).isEmpty();
        assertThatThrownBy(PermissionContextHolder::require)
                .isInstanceOf(PermissionContextMissingException.class);
    }
}

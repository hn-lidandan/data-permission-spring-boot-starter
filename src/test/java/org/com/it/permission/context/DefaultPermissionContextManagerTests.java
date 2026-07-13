package org.com.it.permission.context;

import org.com.it.permission.cache.CaffeinePermissionContextCache;
import org.com.it.permission.config.DataPermissionProperties;
import org.com.it.permission.exception.PermissionDeniedException;
import org.com.it.permission.identity.PermissionIdentity;
import org.com.it.permission.model.PermissionContext;
import org.com.it.permission.service.PermissionServiceClient;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 默认权限上下文管理器测试。
 *
 * <p>这里重点验证 ContextManager 的三条核心规则：缓存命中不回源、denyAll 默认拒绝、
 * expiresAt 过期默认拒绝。</p>
 */
class DefaultPermissionContextManagerTests {

    @Test
    void shouldLoadContextOnceAndThenUseCache() {
        // 第一次获取上下文会调用权限服务客户端，第二次同一个 key 应该直接命中本地缓存。
        CountingPermissionServiceClient client = new CountingPermissionServiceClient(false);
        DefaultPermissionContextManager manager = new DefaultPermissionContextManager(
                new CaffeinePermissionContextCache(com.github.benmanes.caffeine.cache.Caffeine.newBuilder().build()),
                client,
                new DataPermissionProperties()
        );
        PermissionIdentity identity = identity();

        PermissionContext first = manager.getContext(identity);
        PermissionContext second = manager.getContext(identity);

        assertThat(first).isSameAs(second);
        assertThat(client.callCount()).isEqualTo(1);
        assertThat(manager.buildCacheKey(identity)).isEqualTo("log-service:t001:u001:d001:r001");
    }

    @Test
    void shouldUseDifferentCacheWhenDeptIdDifferent() {
        // 同一用户切换部门时，权限上下文可能不同，不能复用旧缓存。
        CountingPermissionServiceClient client = new CountingPermissionServiceClient(false);
        DefaultPermissionContextManager manager = new DefaultPermissionContextManager(
                new CaffeinePermissionContextCache(com.github.benmanes.caffeine.cache.Caffeine.newBuilder().build()),
                client,
                new DataPermissionProperties()
        );

        PermissionContext first = manager.getContext(identity("d001", "r001"));
        PermissionContext second = manager.getContext(identity("d002", "r001"));

        assertThat(first).isNotSameAs(second);
        assertThat(client.callCount()).isEqualTo(2);
    }

    @Test
    void shouldUseDifferentCacheWhenRoleIdDifferent() {
        // 同一用户切换角色时，权限上下文可能不同，不能复用旧缓存。
        CountingPermissionServiceClient client = new CountingPermissionServiceClient(false);
        DefaultPermissionContextManager manager = new DefaultPermissionContextManager(
                new CaffeinePermissionContextCache(com.github.benmanes.caffeine.cache.Caffeine.newBuilder().build()),
                client,
                new DataPermissionProperties()
        );

        PermissionContext first = manager.getContext(identity("d001", "r001"));
        PermissionContext second = manager.getContext(identity("d001", "r002"));

        assertThat(first).isNotSameAs(second);
        assertThat(client.callCount()).isEqualTo(2);
    }

    @Test
    void shouldDenyWhenCacheKeyIdentityFieldMissing() {
        // 构造缓存 key 前先校验完整身份，避免缺少部门或角色时误命中旧缓存。
        DefaultPermissionContextManager manager = new DefaultPermissionContextManager(
                new CaffeinePermissionContextCache(com.github.benmanes.caffeine.cache.Caffeine.newBuilder().build()),
                new CountingPermissionServiceClient(false),
                new DataPermissionProperties()
        );
        PermissionIdentity identity = PermissionIdentity.builder()
                .tenantId("t001")
                .userId("u001")
                .deptId("d001")
                .clientApp("log-service")
                .build();

        assertThatThrownBy(() -> manager.getContext(identity))
                .isInstanceOf(PermissionDeniedException.class)
                .hasMessageContaining("role_id");
    }

    @Test
    void shouldDenyWhenLoadedContextIsDenyAll() {
        // 权限服务明确返回 denyAll=true 时，SDK 必须拒绝，不能写入缓存后放行。
        DefaultPermissionContextManager manager = new DefaultPermissionContextManager(
                new CaffeinePermissionContextCache(com.github.benmanes.caffeine.cache.Caffeine.newBuilder().build()),
                new CountingPermissionServiceClient(true, LocalDateTime.now().plusMinutes(1)),
                new DataPermissionProperties()
        );

        assertThatThrownBy(() -> manager.getContext(identity()))
                .isInstanceOf(PermissionDeniedException.class);
    }

    @Test
    void shouldDenyWhenLoadedContextIsExpired() {
        // 权限上下文业务有效期已经过期时，即使刚从权限服务拿到，也不能放行。
        DefaultPermissionContextManager manager = new DefaultPermissionContextManager(
                new CaffeinePermissionContextCache(com.github.benmanes.caffeine.cache.Caffeine.newBuilder().build()),
                new CountingPermissionServiceClient(false, LocalDateTime.now().minusMinutes(1)),
                new DataPermissionProperties()
        );

        assertThatThrownBy(() -> manager.getContext(identity()))
                .isInstanceOf(PermissionDeniedException.class);
    }

    /**
     * 构造一个固定身份，方便验证缓存 key 格式。
     */
    private PermissionIdentity identity() {
        return identity("d001", "r001");
    }

    private PermissionIdentity identity(String deptId, String roleId) {
        return PermissionIdentity.builder()
                .tenantId("t001")
                .userId("u001")
                .deptId(deptId)
                .roleId(roleId)
                .clientApp("log-service")
                .build();
    }

    /**
     * 测试用权限服务客户端。
     *
     * <p>它记录被调用次数，并允许测试控制 denyAll 和 expiresAt 返回值。</p>
     */
    static class CountingPermissionServiceClient implements PermissionServiceClient {

        private final AtomicInteger callCount = new AtomicInteger();

        private final boolean denyAll;

        private final LocalDateTime expiresAt;

        CountingPermissionServiceClient(boolean denyAll) {
            this(denyAll, LocalDateTime.now().plusMinutes(1));
        }

        CountingPermissionServiceClient(boolean denyAll, LocalDateTime expiresAt) {
            this.denyAll = denyAll;
            this.expiresAt = expiresAt;
        }

        @Override
        public PermissionContext queryContext(PermissionIdentity identity) {
            callCount.incrementAndGet();
            PermissionContext context = new PermissionContext();
            context.setTenantId(identity.getTenantId());
            context.setUserId(identity.getUserId());
            context.setDenyAll(denyAll);
            context.setExpiresAt(expiresAt);
            return context;
        }

        int callCount() {
            return callCount.get();
        }
    }
}

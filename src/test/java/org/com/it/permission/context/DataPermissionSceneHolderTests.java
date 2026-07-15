package org.com.it.permission.context;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 数据权限场景 ThreadLocal 测试。
 */
class DataPermissionSceneHolderTests {

    @AfterEach
    void tearDown() {
        DataPermissionSceneHolder.clear();
    }

    @Test
    void shouldUseQueryAsDefaultScene() {
        assertThat(DataPermissionSceneHolder.get()).isEmpty();
        assertThat(DataPermissionSceneHolder.current()).isEqualTo("QUERY");
    }

    @Test
    void shouldNormalizeAndClearScene() {
        DataPermissionSceneHolder.set(" detail ");

        assertThat(DataPermissionSceneHolder.get()).contains("DETAIL");
        assertThat(DataPermissionSceneHolder.current()).isEqualTo("DETAIL");

        DataPermissionSceneHolder.clear();

        assertThat(DataPermissionSceneHolder.current()).isEqualTo("QUERY");
    }
}

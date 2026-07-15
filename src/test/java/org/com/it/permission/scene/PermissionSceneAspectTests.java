package org.com.it.permission.scene;

import org.com.it.permission.context.DataPermissionSceneHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 场景注解切面测试。
 */
class PermissionSceneAspectTests {

    @AfterEach
    void tearDown() {
        DataPermissionSceneHolder.clear();
    }

    @Test
    void shouldSetAndClearSceneForMethodAnnotation() {
        SceneService proxy = proxy(new SceneService());

        String scene = proxy.detailScene();

        assertThat(scene).isEqualTo(PermissionScenes.DETAIL);
        assertThat(DataPermissionSceneHolder.get()).isEmpty();
    }

    @Test
    void shouldRestorePreviousSceneAfterMethodReturn() {
        DataPermissionSceneHolder.set(PermissionScenes.EXPORT);
        SceneService proxy = proxy(new SceneService());

        String scene = proxy.detailScene();

        assertThat(scene).isEqualTo(PermissionScenes.DETAIL);
        assertThat(DataPermissionSceneHolder.get()).contains(PermissionScenes.EXPORT);
    }

    @Test
    void shouldUseClassAnnotationWhenMethodAnnotationMissing() {
        ClassSceneService proxy = proxy(new ClassSceneService());

        String scene = proxy.currentScene();

        assertThat(scene).isEqualTo(PermissionScenes.EXPORT);
        assertThat(DataPermissionSceneHolder.get()).isEmpty();
    }

    private <T> T proxy(T target) {
        AspectJProxyFactory factory = new AspectJProxyFactory(target);
        factory.setProxyTargetClass(true);
        factory.addAspect(new PermissionSceneAspect());
        return factory.getProxy();
    }

    static class SceneService {

        @PermissionScene(PermissionScenes.DETAIL)
        String detailScene() {
            return DataPermissionSceneHolder.get().orElse("NONE");
        }
    }

    @PermissionScene(PermissionScenes.EXPORT)
    static class ClassSceneService {

        String currentScene() {
            return DataPermissionSceneHolder.get().orElse("NONE");
        }
    }
}

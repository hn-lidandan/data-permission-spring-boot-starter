package org.com.it.permission.scene;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.com.it.permission.context.DataPermissionSceneHolder;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;
import java.util.Optional;

/**
 * 处理 {@link PermissionScene} 注解，把场景写入当前线程。
 */
@Aspect
public class PermissionSceneAspect {

    /**
     * 拦截类或方法上的 PermissionScene 注解。
     */
    @Around("@within(org.com.it.permission.scene.PermissionScene) || @annotation(org.com.it.permission.scene.PermissionScene)")
    public Object bindScene(ProceedingJoinPoint joinPoint) throws Throwable {
        PermissionScene scene = resolveScene(joinPoint);
        if (scene == null) {
            return joinPoint.proceed();
        }

        String sceneValue = scene.value();
        if (!StringUtils.hasText(sceneValue)) {
            throw new IllegalStateException("@PermissionScene value must not be empty");
        }

        Optional<String> previousScene = DataPermissionSceneHolder.get();
        try {
            DataPermissionSceneHolder.set(sceneValue);
            return joinPoint.proceed();
        } finally {
            restoreScene(previousScene);
        }
    }

    /**
     * 解析当前连接点上的场景注解，方法注解优先于类注解。
     */
    private PermissionScene resolveScene(ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        PermissionScene scene = AnnotatedElementUtils.findMergedAnnotation(method, PermissionScene.class);
        if (scene != null) {
            return scene;
        }

        Class<?> targetClass = joinPoint.getTarget() == null
                ? method.getDeclaringClass()
                : joinPoint.getTarget().getClass();
        try {
            Method targetMethod = targetClass.getMethod(method.getName(), method.getParameterTypes());
            scene = AnnotatedElementUtils.findMergedAnnotation(targetMethod, PermissionScene.class);
            if (scene != null) {
                return scene;
            }
        } catch (NoSuchMethodException ignored) {
            // JDK 代理或桥接方法场景下可能拿不到目标方法，继续走类注解兜底。
        }

        scene = AnnotatedElementUtils.findMergedAnnotation(targetClass, PermissionScene.class);
        if (scene != null) {
            return scene;
        }
        return AnnotatedElementUtils.findMergedAnnotation(method.getDeclaringClass(), PermissionScene.class);
    }

    /**
     * 恢复进入方法前的场景，避免嵌套调用污染外层场景。
     */
    private void restoreScene(Optional<String> previousScene) {
        if (previousScene.isPresent()) {
            DataPermissionSceneHolder.set(previousScene.get());
        } else {
            DataPermissionSceneHolder.clear();
        }
    }
}

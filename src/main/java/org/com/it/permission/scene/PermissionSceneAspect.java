package org.com.it.permission.scene;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.com.it.permission.context.DataPermissionSceneHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;
import java.util.Optional;

/**
 * 处理 {@link PermissionScene} 注解，把场景写入当前线程。
 *
 * <p>注意：Web 请求入口（Controller）的场景绑定由 {@code DataPermissionWebInterceptor} 负责，
 * 拦截器绑定的场景生命周期覆盖响应体序列化阶段。本切面用于非 Web 入口（定时任务、MQ 消费者）
 * 以及 service 内部临时切换场景（如导出流程中切到 EXPORT）；方法返回时恢复外层场景，
 * 不会清掉拦截器绑定的请求级场景。</p>
 */
@Aspect
public class PermissionSceneAspect {

    private static final Logger log = LoggerFactory.getLogger(PermissionSceneAspect.class);

    /**
     * 拦截类或方法上的 PermissionScene 注解。
     */
    @Around("@within(org.com.it.permission.scene.PermissionScene) || @annotation(org.com.it.permission.scene.PermissionScene)")
    public Object bindScene(ProceedingJoinPoint joinPoint) throws Throwable {
        PermissionScene scene = resolveScene(joinPoint);
        if (scene == null) {
            log.info("[data-permission] no @PermissionScene resolved on {}, proceed without scene",
                    joinPoint.getSignature().toShortString());
            return joinPoint.proceed();
        }

        String sceneValue = scene.value();
        if (!StringUtils.hasText(sceneValue)) {
            throw new IllegalStateException("@PermissionScene value must not be empty");
        }

        Optional<String> previousScene = DataPermissionSceneHolder.get();
        try {
            DataPermissionSceneHolder.set(sceneValue);
            log.debug("[data-permission] scene [{}] bound before {} (previous scene: {})",
                    sceneValue, joinPoint.getSignature().toShortString(), previousScene.orElse("<none>"));
            return joinPoint.proceed();
        } finally {
            restoreScene(previousScene);
            log.debug("[data-permission] scene [{}] unbound after {} returned, restored to [{}]",
                    sceneValue, joinPoint.getSignature().toShortString(), previousScene.orElse("<none>"));
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

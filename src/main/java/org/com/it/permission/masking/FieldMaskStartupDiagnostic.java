package org.com.it.permission.masking;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter;
import tools.jackson.databind.JacksonModule;
import tools.jackson.databind.ObjectMapper;

/**
 * 启动自检：确认 Spring MVC 实际用于写响应体的 Mapper 是否注册了字段权限 Module。
 *
 * <p>字段脱敏失效的常见原因是业务服务自定义了 JsonMapper / MessageConverter，
 * 导致 {@link PermissionJacksonModule} 没有进入 MVC 序列化链路。这里在应用启动完成后
 * 主动检查并把结论打到日志里，方便排查。</p>
 */
public class FieldMaskStartupDiagnostic implements ApplicationListener<ApplicationReadyEvent> {

    private static final Logger log = LoggerFactory.getLogger(FieldMaskStartupDiagnostic.class);

    private final ObjectProvider<RequestMappingHandlerAdapter> handlerAdapters;

    /**
     * 创建启动自检器。
     */
    public FieldMaskStartupDiagnostic(ObjectProvider<RequestMappingHandlerAdapter> handlerAdapters) {
        this.handlerAdapters = handlerAdapters;
    }

    /**
     * 应用就绪后检查 MVC 消息转换器里的 Mapper。
     */
    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        RequestMappingHandlerAdapter adapter = handlerAdapters.getIfAvailable();
        if (adapter == null) {
            log.warn("[data-permission] field-mask self-check: no RequestMappingHandlerAdapter found, "
                    + "cannot verify MVC mapper registration");
            return;
        }

        boolean foundJsonConverter = false;
        for (HttpMessageConverter<?> converter : adapter.getMessageConverters()) {
            if (!(converter instanceof JacksonJsonHttpMessageConverter jacksonConverter)) {
                continue;
            }
            foundJsonConverter = true;
            ObjectMapper mapper = jacksonConverter.getMapper();
            boolean registered = mapper.registeredModules().stream()
                    .anyMatch(module -> module instanceof PermissionJacksonModule);
            if (registered) {
                log.info("[data-permission] field-mask self-check OK: converter [{}] uses mapper [{}] "
                                + "with PermissionJacksonModule registered",
                        converter.getClass().getName(), System.identityHashCode(mapper));
            } else {
                log.error("[data-permission] field-mask self-check FAILED: converter [{}] uses mapper [{}] "
                                + "WITHOUT PermissionJacksonModule — field masking will NOT work for responses "
                                + "serialized by this converter. Registered modules: {}. "
                                + "Likely cause: the service builds its own JsonMapper/MessageConverter and bypasses "
                                + "Spring Boot's auto-configured JsonMapper (e.g. JsonMapper.builder() without the "
                                + "SDK module, extends WebMvcConfigurationSupport, or a custom converter bean)",
                        converter.getClass().getName(), System.identityHashCode(mapper),
                        mapper.registeredModules().stream().map(JacksonModule::getModuleName).toList());
            }
        }

        if (!foundJsonConverter) {
            // Boot 4 默认注册 JacksonJsonHttpMessageConverter；走到这里说明业务服务替换了 JSON 序列化方案。
            boolean hasLegacyJackson2 = adapter.getMessageConverters().stream()
                    .map(c -> c.getClass().getName())
                    .anyMatch(name -> name.equals("org.springframework.http.converter.json.MappingJackson2HttpMessageConverter"));
            log.error("[data-permission] field-mask self-check FAILED: no JacksonJsonHttpMessageConverter (Jackson 3) "
                            + "found in Spring MVC message converters — field masking will NOT work. "
                            + "legacyJackson2ConverterPresent={}. Converters: {}",
                    hasLegacyJackson2,
                    adapter.getMessageConverters().stream().map(c -> c.getClass().getSimpleName()).toList());
        }
    }
}

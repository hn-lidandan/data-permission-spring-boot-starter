package org.com.it.permission.model;

import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;

/**
 * 权限上下文时间字段反序列化器。
 *
 * <p>SDK 内部统一使用 LocalDateTime。权限服务如果返回 {@code 2026-06-29T18:00:00}，
 * 会直接按 LocalDateTime 解析；如果返回 {@code 2026-06-29T18:00:00+08:00}，
 * 会取其中的本地日期时间部分，得到 {@code 2026-06-29T18:00:00}。</p>
 */
public class FlexibleLocalDateTimeDeserializer extends ValueDeserializer<LocalDateTime> {

    @Override
    public LocalDateTime deserialize(JsonParser parser, DeserializationContext context) {
        String value = parser.getString();
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmedValue = value.trim();
        try {
            return LocalDateTime.parse(trimmedValue);
        } catch (RuntimeException ignored) {
            return OffsetDateTime.parse(trimmedValue).toLocalDateTime();
        }
    }
}

package org.com.it.permission.masking;

import org.springframework.util.StringUtils;

import java.util.Locale;

/**
 * 内置字段脱敏规则。
 */
public final class MaskRules {

    private static final String DEFAULT_MASK = "****";

    /**
     * 工具类不允许实例化。
     */
    private MaskRules() {
    }

    /**
     * 根据规则名称执行脱敏。
     */
    public static Object mask(String rule, Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value);
        if (!StringUtils.hasText(text)) {
            return text;
        }

        return switch (normalize(rule)) {
            case "PHONE" -> maskPhone(text);
            case "EMAIL" -> maskEmail(text);
            case "ID_CARD" -> maskIdCard(text);
            case "TOKEN" -> maskToken(text);
            case "ACCESS_KEY" -> maskAccessKey(text);
            default -> maskDefault(text);
        };
    }

    /**
     * 手机号脱敏，保留前三位和后四位。
     */
    private static String maskPhone(String text) {
        if (text.length() < 7) {
            return maskDefault(text);
        }
        return text.substring(0, 3) + DEFAULT_MASK + text.substring(text.length() - 4);
    }

    /**
     * 邮箱脱敏，保留首字母和域名。
     */
    private static String maskEmail(String text) {
        int atIndex = text.indexOf('@');
        if (atIndex <= 0 || atIndex == text.length() - 1) {
            return maskDefault(text);
        }
        return text.charAt(0) + DEFAULT_MASK + text.substring(atIndex);
    }

    /**
     * 身份证脱敏，保留前三位和后四位。
     */
    private static String maskIdCard(String text) {
        if (text.length() < 8) {
            return maskDefault(text);
        }
        return text.substring(0, 3) + "***********" + text.substring(text.length() - 4);
    }

    /**
     * Token 脱敏，保留前缀 sk-。
     */
    private static String maskToken(String text) {
        if (text.startsWith("sk-")) {
            return "sk-************";
        }
        return maskDefault(text);
    }

    /**
     * AccessKey 脱敏，保留 AKIA 前缀。
     */
    private static String maskAccessKey(String text) {
        if (text.startsWith("AKIA")) {
            return "AKIA************";
        }
        return maskDefault(text);
    }

    /**
     * 默认脱敏，短文本全部替换，长文本保留前两位和后两位。
     */
    private static String maskDefault(String text) {
        if (text.length() <= 4) {
            return DEFAULT_MASK;
        }
        return text.substring(0, 2) + DEFAULT_MASK + text.substring(text.length() - 2);
    }

    /**
     * 归一化脱敏规则名称。
     */
    private static String normalize(String rule) {
        return StringUtils.hasText(rule) ? rule.trim().toUpperCase(Locale.ROOT) : "";
    }
}

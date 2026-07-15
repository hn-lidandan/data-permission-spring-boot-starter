package org.com.it.permission.masking;

/**
 * 字段策略处理结果。
 */
class FieldMaskingResult {

    private final boolean applied;

    private final Object value;

    private FieldMaskingResult(boolean applied, Object value) {
        this.applied = applied;
        this.value = value;
    }

    /**
     * 创建未处理结果。
     */
    static FieldMaskingResult unchanged(Object value) {
        return new FieldMaskingResult(false, value);
    }

    /**
     * 创建已处理结果。
     */
    static FieldMaskingResult applied(Object value) {
        return new FieldMaskingResult(true, value);
    }

    /**
     * 判断是否命中了字段策略。
     */
    boolean isApplied() {
        return applied;
    }

    /**
     * 返回最终字段值。
     */
    Object getValue() {
        return value;
    }
}

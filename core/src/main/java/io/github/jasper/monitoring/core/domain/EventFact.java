package io.github.jasper.monitoring.core.domain;

import io.github.jasper.monitoring.api.fact.FactSource;
import java.util.Objects;

/**
 * 持久化在安全事件中的不可变事实快照。
 *
 * <p>该对象由编解码层生成并已完成类型归一化，供规则评估与审计查询复用。</p>
 */
public final class EventFact {
    private final String key;
    private final String valueType;
    private final String valueText;
    private final FactSource source;

    /**
     * 创建一条不可变事实快照。
     *
     * @param key 事实键
     * @param valueType 事实值类型标识
     * @param valueText 序列化后的事实值文本
     * @param source 事实来源
     */
    public EventFact(String key, String valueType, String valueText, FactSource source) {
        this.key = bounded(key, "key", 128);
        this.valueType = bounded(valueType, "valueType", 256);
        this.valueText = bounded(valueText, "valueText", 2048);
        this.source = Objects.requireNonNull(source, "source");
    }

    /** @return 事实键 */
    public String getKey() { return key; }
    /** @return 事实值类型标识 */
    public String getValueType() { return valueType; }
    /** @return 序列化后的事实值文本 */
    public String getValueText() { return valueText; }
    /** @return 事实来源 */
    public FactSource getSource() { return source; }

    /**
     * 校验必填文本并限制长度。
     *
     * @param value 待校验文本
     * @param name 字段名
     * @param maximum 最大长度
     * @return 通过校验的原始文本
     */
    private static String bounded(String value, String name, int maximum) {
        Objects.requireNonNull(value, name);
        if (value.trim().isEmpty() || value.length() > maximum) {
            throw new IllegalArgumentException(name + " is blank or too long");
        }
        return value;
    }
}

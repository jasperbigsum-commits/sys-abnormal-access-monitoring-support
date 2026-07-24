package io.github.jasper.monitoring.api;

/**
 * 输入事实用于监测规则前的质量结论。
 */
public enum EventInputStatus {
    /** 所有已启用规则所需的事实均可用于评估。 */
    VALID,
    /** 缺少部分事实，只有依赖这些事实的规则不可评估。 */
    INCOMPLETE,
    /** 输入包含无法安全用于规则评估的事实。 */
    INVALID,
    /** 历史事件或外部重建事件的输入质量未知。 */
    UNKNOWN
}

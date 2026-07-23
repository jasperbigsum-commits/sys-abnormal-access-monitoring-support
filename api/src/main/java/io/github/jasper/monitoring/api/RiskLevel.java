package io.github.jasper.monitoring.api;

/** 命中监测规则后分配的相对风险等级。 */
public enum RiskLevel {
    /** 提示性或影响较低的行为。 */
    LOW,
    /** 需要及时复核的行为。 */
    MEDIUM,
    /** 可能需要立即控制或应急响应的行为。 */
    HIGH
}

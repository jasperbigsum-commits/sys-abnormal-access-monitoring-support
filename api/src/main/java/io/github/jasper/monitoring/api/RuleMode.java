package io.github.jasper.monitoring.api;

/** 单条规则的运行模式，用于按阶段启用规则响应。 */
public enum RuleMode {
    /** 不评估该规则。 */
    DISABLED,
    /** 评估并保留证据，但不生成告警或请求控制。 */
    OBSERVE,
    /** 评估并生成告警，但不请求控制动作。 */
    ALERT_ONLY,
    /** 评估、告警并请求执行规则配置的控制动作。 */
    ENFORCE
}

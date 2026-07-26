package io.github.jasper.monitoring.api.rule;

/** Runtime response level for a statically defined rule. */
public enum RuleMode {
    DISABLED,
    OBSERVE,
    ALERT_ONLY,
    ENFORCE
}

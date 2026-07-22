package io.github.jasper.monitoring.api;

/** Per-rule operating mode, allowing deployments to phase in rule responses. */
public enum RuleMode {
    /** Do not evaluate the rule. */
    DISABLED,
    /** Evaluate and retain evidence without generating an alert or control. */
    OBSERVE,
    /** Evaluate and generate alerts without requesting a control action. */
    ALERT_ONLY,
    /** Evaluate, alert, and request the rule's configured control action. */
    ENFORCE
}

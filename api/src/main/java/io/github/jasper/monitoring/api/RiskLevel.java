package io.github.jasper.monitoring.api;

/** Relative severity assigned by a matched monitoring rule. */
public enum RiskLevel {
    /** Informational or low-impact behavior. */
    LOW,
    /** Behavior that requires timely review. */
    MEDIUM,
    /** Behavior that may require immediate control or incident response. */
    HIGH
}

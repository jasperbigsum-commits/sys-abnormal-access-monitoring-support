package io.github.jasper.monitoring.api;

/** Runtime posture for requested monitoring controls. */
public enum MonitoringMode {
    /** Collect evidence, evaluate rules, and create alerts without applying controls. */
    OBSERVE,
    /** Apply requested controls through registered host control handlers. */
    ENFORCE
}

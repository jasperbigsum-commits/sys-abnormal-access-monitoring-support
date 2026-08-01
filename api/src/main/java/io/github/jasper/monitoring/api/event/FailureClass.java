package io.github.jasper.monitoring.api.event;

/** Classification available only for technical FAILURE outcomes. */
public enum FailureClass {
    VALIDATION,
    AUTHORIZATION,
    BUSINESS,
    INFRASTRUCTURE,
    UNKNOWN
}

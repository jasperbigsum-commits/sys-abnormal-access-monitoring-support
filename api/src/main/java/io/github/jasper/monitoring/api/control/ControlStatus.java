package io.github.jasper.monitoring.api.control;

/** Durable lifecycle of a control request. */
public enum ControlStatus {
    PENDING,
    AWAITING_APPROVAL,
    SUCCEEDED,
    FAILED,
    SKIPPED,
    REJECTED
}

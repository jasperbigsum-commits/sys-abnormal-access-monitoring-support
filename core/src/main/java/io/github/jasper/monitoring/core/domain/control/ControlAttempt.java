package io.github.jasper.monitoring.core.domain.control;

import io.github.jasper.monitoring.api.control.ControlStatus;
import java.time.Instant;
import java.util.Objects;

/** Append-only execution attempt. */
public final class ControlAttempt {
    private final String controlId; private final int attemptNo;
    private final ControlStatus status; private final String failureReason; private final Instant attemptedAt;
    public ControlAttempt(String controlId, int attemptNo, ControlStatus status, String failureReason, Instant attemptedAt) {
        this.controlId = Objects.requireNonNull(controlId, "controlId"); this.attemptNo = attemptNo;
        this.status = Objects.requireNonNull(status, "status"); this.failureReason = failureReason;
        this.attemptedAt = Objects.requireNonNull(attemptedAt, "attemptedAt");
    }
    public String getControlId() { return controlId; }
    public int getAttemptNo() { return attemptNo; }
    public ControlStatus getStatus() { return status; }
    public String getFailureReason() { return failureReason; }
    public Instant getAttemptedAt() { return attemptedAt; }
}

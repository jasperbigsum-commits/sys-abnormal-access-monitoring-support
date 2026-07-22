package io.github.jasper.monitoring.core;

import io.github.jasper.monitoring.api.ControlStatus;

/** Result returned by a host control handler and retained for audit and retry decisions. */
public final class ControlExecution {
    private final String controlId;
    private final String idempotencyKey;
    private final ControlStatus status;
    private final String failureReason;
    private final boolean idempotentReplay;
    private ControlExecution(String controlId, String idempotencyKey, ControlStatus status, String failureReason, boolean idempotentReplay) {
        this.controlId = controlId;
        this.idempotencyKey = idempotencyKey;
        this.status = status;
        this.failureReason = failureReason;
        this.idempotentReplay = idempotentReplay;
    }
    /** @return a successful execution result for the supplied idempotency key */
    public static ControlExecution succeeded(String key) { return new ControlExecution(key, key, ControlStatus.SUCCEEDED, null, false); }
    /** @return a failed execution result with a non-sensitive failure reason */
    public static ControlExecution failed(String key, String reason) { return new ControlExecution(key, key, ControlStatus.FAILED, reason, false); }
    /** @return an execution result showing that the host intentionally skipped the action */
    public static ControlExecution skipped(String key, String reason) { return new ControlExecution(key, key, ControlStatus.SKIPPED, reason, false); }
    /** @return an equivalent result flagged as a replay instead of a new execution */
    public ControlExecution replay() { return new ControlExecution(controlId, idempotencyKey, status, failureReason, true); }
    public String getControlId() { return controlId; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public ControlStatus getStatus() { return status; }
    public String getFailureReason() { return failureReason; }
    public boolean isIdempotentReplay() { return idempotentReplay; }
    public boolean isSucceeded() { return status == ControlStatus.SUCCEEDED; }
}

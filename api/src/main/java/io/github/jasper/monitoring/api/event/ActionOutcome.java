package io.github.jasper.monitoring.api.event;

import io.github.jasper.monitoring.api.SecurityEventResult;
import io.github.jasper.monitoring.api.code.ReasonCode;
import java.util.Objects;

/** Framework-owned final result of an action invocation. */
public final class ActionOutcome {
    private final SecurityEventResult result;
    private final ReasonCode reason;
    private final FailureClass failureClass;
    private final long latencyMs;

    private ActionOutcome(SecurityEventResult result, ReasonCode reason,
                          FailureClass failureClass, long latencyMs) {
        this.result = Objects.requireNonNull(result, "result");
        if (result != SecurityEventResult.SUCCESS) {
            this.reason = Objects.requireNonNull(reason, "reason");
        } else if (reason != null) {
            throw new IllegalArgumentException("Success outcome cannot have a reason");
        } else {
            this.reason = null;
        }
        if (latencyMs < 0L) throw new IllegalArgumentException("latencyMs must not be negative");
        if (result == SecurityEventResult.FAILURE && failureClass == null) {
            throw new IllegalArgumentException("Failure outcome requires failure class");
        }
        if (result != SecurityEventResult.FAILURE && failureClass != null) {
            throw new IllegalArgumentException("Only failure outcome may have a failure class");
        }
        this.failureClass = failureClass;
        this.latencyMs = latencyMs;
    }

    public static ActionOutcome success(long latencyMs) {
        return new ActionOutcome(SecurityEventResult.SUCCESS, null, null, latencyMs);
    }
    public static ActionOutcome failure(ReasonCode reason, FailureClass failureClass, long latencyMs) {
        return new ActionOutcome(SecurityEventResult.FAILURE, reason,
            Objects.requireNonNull(failureClass, "failureClass"), latencyMs);
    }
    public static ActionOutcome denied(ReasonCode reason, long latencyMs) {
        return new ActionOutcome(SecurityEventResult.DENIED, reason, null, latencyMs);
    }
    public SecurityEventResult getResult() { return result; }
    public ReasonCode getReason() { return reason; }
    public FailureClass getFailureClass() { return failureClass; }
    public long getLatencyMs() { return latencyMs; }
}

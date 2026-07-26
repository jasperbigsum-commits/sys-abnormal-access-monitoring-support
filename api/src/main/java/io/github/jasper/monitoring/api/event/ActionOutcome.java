package io.github.jasper.monitoring.api.event;

import io.github.jasper.monitoring.api.SecurityEventResult;
import io.github.jasper.monitoring.api.SecurityFieldSanitizer;
import java.util.Objects;

/** Framework-owned final result of an action invocation. */
public final class ActionOutcome {
    public enum ExceptionClassification {
        VALIDATION,
        AUTHORIZATION,
        BUSINESS,
        INFRASTRUCTURE,
        UNKNOWN
    }

    private final SecurityEventResult result;
    private final String reasonCode;
    private final ExceptionClassification exceptionClassification;
    private final long latencyMs;

    private ActionOutcome(SecurityEventResult result, String reasonCode,
                          ExceptionClassification exceptionClassification, long latencyMs) {
        this.result = Objects.requireNonNull(result, "result");
        this.reasonCode = SecurityFieldSanitizer.text(reasonCode, 128);
        if (latencyMs < 0L) throw new IllegalArgumentException("latencyMs must not be negative");
        if (result == SecurityEventResult.FAILURE && exceptionClassification == null) {
            throw new IllegalArgumentException("Failure outcome requires exception classification");
        }
        if (result != SecurityEventResult.FAILURE && exceptionClassification != null) {
            throw new IllegalArgumentException("Only failure outcome may classify an exception");
        }
        this.exceptionClassification = exceptionClassification;
        this.latencyMs = latencyMs;
    }

    public static ActionOutcome success(long latencyMs) {
        return new ActionOutcome(SecurityEventResult.SUCCESS, null, null, latencyMs);
    }
    public static ActionOutcome failure(String reasonCode, ExceptionClassification classification, long latencyMs) {
        return new ActionOutcome(SecurityEventResult.FAILURE, reasonCode,
            Objects.requireNonNull(classification, "classification"), latencyMs);
    }
    public static ActionOutcome denied(String reasonCode, long latencyMs) {
        return new ActionOutcome(SecurityEventResult.DENIED, reasonCode, null, latencyMs);
    }
    public SecurityEventResult getResult() { return result; }
    public String getReasonCode() { return reasonCode; }
    public ExceptionClassification getExceptionClassification() { return exceptionClassification; }
    public long getLatencyMs() { return latencyMs; }
}

package io.github.jasper.monitoring.api.event;

import io.github.jasper.monitoring.api.SecurityEventResult;
import io.github.jasper.monitoring.api.SecurityFieldSanitizer;
import java.util.Objects;

/** Framework-owned final result of an action invocation. */
public final class ActionOutcome {
    private final SecurityEventResult result;
    private final String reasonCode;

    private ActionOutcome(SecurityEventResult result, String reasonCode) {
        this.result = Objects.requireNonNull(result, "result");
        this.reasonCode = SecurityFieldSanitizer.text(reasonCode, 128);
    }

    public static ActionOutcome of(SecurityEventResult result) { return new ActionOutcome(result, null); }
    public static ActionOutcome success() { return of(SecurityEventResult.SUCCESS); }
    public static ActionOutcome failure(String reasonCode) { return new ActionOutcome(SecurityEventResult.FAILURE, reasonCode); }
    public static ActionOutcome denied(String reasonCode) { return new ActionOutcome(SecurityEventResult.DENIED, reasonCode); }
    public SecurityEventResult getResult() { return result; }
    public String getReasonCode() { return reasonCode; }
}

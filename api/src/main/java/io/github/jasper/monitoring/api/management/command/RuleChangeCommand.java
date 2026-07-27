package io.github.jasper.monitoring.api.management.command;

import io.github.jasper.monitoring.api.rule.RuleMode;
import java.util.Objects;

/**
 * Changes a persisted rule by appending a new version.
 *
 * <p>The trusted approver is supplied separately to the service boundary and
 * is never accepted as request data in this command.</p>
 */
public final class RuleChangeCommand extends VersionedReasonCommand {
    private final RuleMode mode;
    private final long threshold;

    private RuleChangeCommand(String ruleId, long expectedVersion, RuleMode mode, long threshold,
                              String reason, String idempotencyKey) {
        super(ruleId, expectedVersion, reason, idempotencyKey);
        if (expectedVersion < 1) {
            throw new IllegalArgumentException("rule expectedVersion must be positive");
        }
        this.mode = Objects.requireNonNull(mode, "mode");
        if (threshold < 1) {
            throw new IllegalArgumentException("threshold must be positive");
        }
        this.threshold = threshold;
    }

    public static RuleChangeCommand of(String ruleId, long expectedVersion, RuleMode mode, long threshold,
                                       String reason, String idempotencyKey) {
        return new RuleChangeCommand(ruleId, expectedVersion, mode, threshold, reason, idempotencyKey);
    }

    public String getRuleId() { return getResourceId(); }
    public RuleMode getMode() { return mode; }
    public long getThreshold() { return threshold; }
}

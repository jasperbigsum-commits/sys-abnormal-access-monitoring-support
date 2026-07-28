package io.github.jasper.monitoring.api.management.command;

import io.github.jasper.monitoring.api.rule.RuleMode;
import java.util.Objects;

/**
 * 以追加新版本方式变更持久化规则。
 *
 * <p>可信审批人应通过服务边界单独传入，不应作为该命令的请求字段。</p>
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

    /** @return 规则变更命令对象 */
    public static RuleChangeCommand of(String ruleId, long expectedVersion, RuleMode mode, long threshold,
                                       String reason, String idempotencyKey) {
        return new RuleChangeCommand(ruleId, expectedVersion, mode, threshold, reason, idempotencyKey);
    }

    /** @return 规则标识 */
    public String getRuleId() { return getResourceId(); }
    /** @return 规则模式 */
    public RuleMode getMode() { return mode; }
    /** @return 阈值 */
    public long getThreshold() { return threshold; }
}

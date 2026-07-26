package io.github.jasper.monitoring.core.domain.rule;


import io.github.jasper.monitoring.core.domain.SecurityEvent;
import io.github.jasper.monitoring.core.domain.RuleMatch;


import io.github.jasper.monitoring.api.ControlActionType;
import io.github.jasper.monitoring.api.RiskLevel;
import io.github.jasper.monitoring.api.rule.RuleType;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Shared immutable response metadata for built-in reusable rules. */
abstract class AbstractDetectionRule implements DetectionRule<AbstractDetectionRule.LegacyRuleType> {
    static final class LegacyRuleType implements RuleType {
        private LegacyRuleType() {
        }
    }
    private static final Duration DEFAULT_CONTROL_TTL = Duration.ofMinutes(15);

    private final String ruleId;
    private final RiskLevel riskLevel;
    private final List<ControlActionType> actions;
    private final String reason;

    AbstractDetectionRule(String ruleId, RiskLevel riskLevel, List<ControlActionType> actions, String reason) {
        this.ruleId = Objects.requireNonNull(ruleId, "ruleId");
        this.riskLevel = Objects.requireNonNull(riskLevel, "riskLevel");
        this.actions = Collections.unmodifiableList(new ArrayList<>(Objects.requireNonNull(actions, "actions")));
        this.reason = Objects.requireNonNull(reason, "reason");
    }

    @Override
    public final String getRuleId() {
        return ruleId;
    }

    protected final Optional<RuleMatch> match(SecurityEvent event) {
        return match(event, event.subject(), DEFAULT_CONTROL_TTL);
    }

    protected final Optional<RuleMatch> match(SecurityEvent event, String subject, Duration controlTtl) {
        String resource = event.getResourceType() == null ? "" : event.getResourceType() + ":" + nullToEmpty(event.getResourceId());
        return Optional.of(new RuleMatch(ruleId, riskLevel, subject, resource, reason, actions, controlTtl));
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}

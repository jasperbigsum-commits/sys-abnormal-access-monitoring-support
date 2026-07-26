package io.github.jasper.monitoring.core.domain.rule;

import io.github.jasper.monitoring.core.domain.RuleMatch;
import io.github.jasper.monitoring.core.domain.SecurityEvent;
import java.util.List;
import java.util.Optional;

/** Compatibility surface retained until the legacy monitoring pipeline is removed. */
@Deprecated
public interface LegacyDetectionRule {
    default String getRuleId() {
        throw new UnsupportedOperationException("Typed rule id belongs to RuleDefinition");
    }

    default Optional<RuleMatch> evaluate(SecurityEvent event, List<SecurityEvent> history) {
        throw new UnsupportedOperationException("Typed rule requires RuleEvaluationContext");
    }
}

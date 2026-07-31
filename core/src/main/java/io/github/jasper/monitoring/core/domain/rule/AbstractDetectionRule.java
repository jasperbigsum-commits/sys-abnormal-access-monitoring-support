package io.github.jasper.monitoring.core.domain.rule;


import io.github.jasper.monitoring.core.domain.SecurityEvent;
import io.github.jasper.monitoring.core.domain.RuleMatch;


import io.github.jasper.monitoring.api.rule.RuleDefinition;
import io.github.jasper.monitoring.api.rule.RuleType;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/** Shared immutable response metadata for built-in reusable rules. */
abstract class AbstractDetectionRule<R extends RuleType> implements DetectionRule<R> {
    private static final Duration DEFAULT_CONTROL_TTL = Duration.ofMinutes(15);

    private final RuleDefinition<R> definition;
    private final String reason;

    AbstractDetectionRule(RuleDefinition<R> definition, String reason) {
        this.definition = Objects.requireNonNull(definition, "definition");
        this.reason = Objects.requireNonNull(reason, "reason");
    }

    @Override
    public final RuleDefinition<R> definition() {
        return definition;
    }

    protected final Optional<RuleMatch> match(SecurityEvent event) {
        return match(event, event.subject(), DEFAULT_CONTROL_TTL);
    }

    protected final Optional<RuleMatch> match(SecurityEvent event, String subject, Duration controlTtl) {
        String resource = event.getResourceType() == null ? "" : event.getResourceType() + ":" + nullToEmpty(event.getResourceId());
        return Optional.of(new RuleMatch(definition.getId(), definition.getRisk(), subject, resource, reason,
            definition.getDisposition(), definition.getRequirements(), definition.getControls(), controlTtl));
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}

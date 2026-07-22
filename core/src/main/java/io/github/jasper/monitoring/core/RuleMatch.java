package io.github.jasper.monitoring.core;

import io.github.jasper.monitoring.api.ControlActionType;
import io.github.jasper.monitoring.api.RiskLevel;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable explanation of a deterministic rule violation and its recommended response. */
public final class RuleMatch {
    private final String ruleId;
    private final RiskLevel riskLevel;
    private final String subject;
    private final String resourceKey;
    private final String reason;
    private final List<ControlActionType> actions;
    private final Duration controlTtl;

    /**
     * Creates a match with the default fifteen-minute control lifetime.
     *
     * @param ruleId stable identifier of the matched rule
     * @param riskLevel assessed severity
     * @param subject user, session, or IP subject used for the response scope
     * @param resourceKey normalized resource scope included in alert deduplication
     * @param reason operator-readable explanation of the match
     * @param actions recommended control actions
     */
    public RuleMatch(String ruleId, RiskLevel riskLevel, String subject, String resourceKey, String reason,
                     List<ControlActionType> actions) {
        this(ruleId, riskLevel, subject, resourceKey, reason, actions, Duration.ofMinutes(15));
    }

    /**
     * Creates a match with an explicit positive lifetime for temporary controls.
     *
     * @param controlTtl duration for host controls requested by this match
     * @throws IllegalArgumentException if {@code controlTtl} is zero or negative
     */
    public RuleMatch(String ruleId, RiskLevel riskLevel, String subject, String resourceKey, String reason,
                     List<ControlActionType> actions, Duration controlTtl) {
        this.ruleId = ruleId;
        this.riskLevel = riskLevel;
        this.subject = subject;
        this.resourceKey = resourceKey == null ? "" : resourceKey;
        this.reason = reason;
        this.actions = Collections.unmodifiableList(new ArrayList<ControlActionType>(actions));
        if (controlTtl == null || controlTtl.isNegative() || controlTtl.isZero()) {
            throw new IllegalArgumentException("controlTtl must be positive");
        }
        this.controlTtl = controlTtl;
    }
    public String getRuleId() { return ruleId; }
    public RiskLevel getRiskLevel() { return riskLevel; }
    public String getSubject() { return subject; }
    public String getResourceKey() { return resourceKey; }
    public String getReason() { return reason; }
    public List<ControlActionType> getActions() { return actions; }
    public Duration getControlTtl() { return controlTtl; }
    /** @return stable alert-deduplication key for this rule, subject, and resource scope */
    public String fingerprint() { return ruleId + "|" + subject + "|" + resourceKey; }
}

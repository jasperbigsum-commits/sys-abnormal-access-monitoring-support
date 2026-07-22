package io.github.jasper.monitoring.core;

import io.github.jasper.monitoring.api.AlertStatus;
import io.github.jasper.monitoring.api.RiskLevel;
import java.time.Instant;

/** Mutable alert summary whose lifecycle history is retained separately as immutable dispositions. */
public final class SecurityAlert {
    private final String alertId;
    private final String ruleId;
    private final RiskLevel riskLevel;
    private final String fingerprint;
    private final String subject;
    private final AlertStatus status;
    private final Instant firstSeen;
    private final Instant lastSeen;
    private final int eventCount;

    /**
     * Rehydrates or creates an alert summary.
     *
     * @param fingerprint stable key used to deduplicate open alerts for the same rule scope
     * @param eventCount number of linked observations represented by this summary
     */
    public SecurityAlert(String alertId, String ruleId, RiskLevel riskLevel, String fingerprint, String subject,
                         AlertStatus status, Instant firstSeen, Instant lastSeen, int eventCount) {
        this.alertId = alertId;
        this.ruleId = ruleId;
        this.riskLevel = riskLevel;
        this.fingerprint = fingerprint;
        this.subject = subject;
        this.status = status;
        this.firstSeen = firstSeen;
        this.lastSeen = lastSeen;
        this.eventCount = eventCount;
    }
    /** @return a new alert in {@link AlertStatus#NEW} state for a rule match */
    public static SecurityAlert open(String alertId, RuleMatch match, Instant at) {
        return new SecurityAlert(alertId, match.getRuleId(), match.getRiskLevel(), match.fingerprint(), match.getSubject(),
            AlertStatus.NEW, at, at, 1);
    }
    /** @return a copy with a refreshed observation time and incremented event count */
    public SecurityAlert observed(Instant at) {
        return new SecurityAlert(alertId, ruleId, riskLevel, fingerprint, subject, status, firstSeen, at, eventCount + 1);
    }
    /** @return a copy with a lifecycle status selected by {@link AlertLifecycleService} */
    public SecurityAlert withStatus(AlertStatus newStatus) {
        return new SecurityAlert(alertId, ruleId, riskLevel, fingerprint, subject, newStatus, firstSeen, lastSeen, eventCount);
    }
    public String getAlertId() { return alertId; }
    public String getRuleId() { return ruleId; }
    public RiskLevel getRiskLevel() { return riskLevel; }
    public String getFingerprint() { return fingerprint; }
    public String getSubject() { return subject; }
    public AlertStatus getStatus() { return status; }
    public Instant getFirstSeen() { return firstSeen; }
    public Instant getLastSeen() { return lastSeen; }
    public int getEventCount() { return eventCount; }
}

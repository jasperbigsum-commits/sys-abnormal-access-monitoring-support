package io.github.jasper.monitoring.core;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Persistence port for immutable events, alert state, audit history, controls, and expiring whitelists.
 * Implementations must preserve control idempotency and never remove appended alert dispositions.
 */
public interface MonitoringRepository {
    /** Persists one server-stamped security event. */
    void saveEvent(SecurityEvent event);
    /** @return events at or after {@code since}, ordered for deterministic rule evaluation */
    List<SecurityEvent> findEventsSince(Instant since);
    /** @return an open alert with this stable rule-scope fingerprint, if present */
    Optional<SecurityAlert> findOpenAlert(String fingerprint);
    /** @return an alert by identifier, including terminal alerts */
    Optional<SecurityAlert> findAlert(String alertId);
    /** Creates or updates the mutable summary state of an alert. */
    void saveAlert(SecurityAlert alert);
    /** Idempotently associates an event with an alert. */
    void linkAlertEvent(String alertId, String eventId);
    /** Appends, rather than overwrites, an operator lifecycle disposition. */
    void appendAlertDisposition(AlertDisposition disposition);
    /** @return all immutable operator dispositions for the alert in chronological order */
    List<AlertDisposition> findAlertDispositions(String alertId);
    /** @return a previously recorded control result for the supplied idempotency key */
    Optional<ControlRecord> findControl(String idempotencyKey);
    /** Persists a control result under its idempotency key. */
    void saveControl(ControlRecord record);
    /** @return whether an unexpired whitelist entry suppresses this rule for the subject at {@code at} */
    boolean isWhitelisted(String ruleId, String subject, Instant at);
    /** Persists an expiring whitelist entry; indefinite entries are not supported. */
    void addWhitelist(WhitelistEntry entry);
}

package io.github.jasper.monitoring.core;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Thread-safe test and local-development repository.
 * Production deployments should use the MyBatis implementation so audit data survives process restarts.
 */
public final class InMemoryMonitoringRepository implements MonitoringRepository {
    private final List<SecurityEvent> events = new ArrayList<SecurityEvent>();
    private final Map<String, SecurityAlert> alerts = new LinkedHashMap<String, SecurityAlert>();
    private final Map<String, ControlRecord> controls = new LinkedHashMap<String, ControlRecord>();
    private final List<WhitelistEntry> whitelists = new ArrayList<WhitelistEntry>();
    private final Map<String, Set<String>> alertEvents = new LinkedHashMap<String, Set<String>>();
    private final Map<String, List<AlertDisposition>> alertDispositions =
        new LinkedHashMap<String, List<AlertDisposition>>();

    @Override
    public synchronized void saveEvent(SecurityEvent event) { events.add(event); }
    @Override
    public synchronized List<SecurityEvent> findEventsSince(Instant since) {
        List<SecurityEvent> result = new ArrayList<SecurityEvent>();
        for (SecurityEvent event : events) {
            if (!event.getOccurredAt().isBefore(since)) { result.add(event); }
        }
        return result;
    }
    @Override
    public synchronized Optional<SecurityAlert> findOpenAlert(String fingerprint) {
        for (SecurityAlert alert : alerts.values()) {
            if (alert.getFingerprint().equals(fingerprint) && alert.getStatus().isOpen()) { return Optional.of(alert); }
        }
        return Optional.empty();
    }
    @Override
    public synchronized Optional<SecurityAlert> findAlert(String alertId) {
        return Optional.ofNullable(alerts.get(alertId));
    }
    @Override
    public synchronized void saveAlert(SecurityAlert alert) { alerts.put(alert.getAlertId(), alert); }
    @Override
    public synchronized void linkAlertEvent(String alertId, String eventId) {
        Set<String> ids = alertEvents.get(alertId);
        if (ids == null) { ids = new LinkedHashSet<String>(); alertEvents.put(alertId, ids); }
        ids.add(eventId);
    }
    @Override
    public synchronized void appendAlertDisposition(AlertDisposition disposition) {
        List<AlertDisposition> dispositions = alertDispositions.get(disposition.getAlertId());
        if (dispositions == null) {
            dispositions = new ArrayList<AlertDisposition>();
            alertDispositions.put(disposition.getAlertId(), dispositions);
        }
        dispositions.add(disposition);
    }
    @Override
    public synchronized List<AlertDisposition> findAlertDispositions(String alertId) {
        List<AlertDisposition> dispositions = alertDispositions.get(alertId);
        if (dispositions == null) { return Collections.emptyList(); }
        return Collections.unmodifiableList(new ArrayList<AlertDisposition>(dispositions));
    }
    @Override
    public synchronized Optional<ControlRecord> findControl(String idempotencyKey) { return Optional.ofNullable(controls.get(idempotencyKey)); }
    @Override
    public synchronized void saveControl(ControlRecord record) { controls.put(record.getCommand().getIdempotencyKey(), record); }
    @Override
    public synchronized boolean isWhitelisted(String ruleId, String subject, Instant at) {
        for (WhitelistEntry entry : whitelists) {
            if (entry.getRuleId().equals(ruleId) && entry.getSubject().equals(subject) && entry.activeAt(at)) { return true; }
        }
        return false;
    }
    @Override
    public synchronized void addWhitelist(WhitelistEntry entry) { whitelists.add(entry); }
    /** @return an immutable snapshot of alert summaries, intended for tests and local diagnostics */
    public synchronized List<SecurityAlert> getAlerts() { return Collections.unmodifiableList(new ArrayList<SecurityAlert>(alerts.values())); }
    /** @return an immutable snapshot of persisted control records, intended for tests and local diagnostics */
    public synchronized List<ControlRecord> getControls() { return Collections.unmodifiableList(new ArrayList<ControlRecord>(controls.values())); }
    /** @return an immutable snapshot of persisted events, intended for tests and local diagnostics */
    public synchronized List<SecurityEvent> getEvents() { return Collections.unmodifiableList(new ArrayList<SecurityEvent>(events)); }
}

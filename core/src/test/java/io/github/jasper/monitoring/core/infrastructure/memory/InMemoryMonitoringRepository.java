package io.github.jasper.monitoring.core.infrastructure.memory;

import io.github.jasper.monitoring.core.domain.WhitelistEntry;
import io.github.jasper.monitoring.core.port.MonitoringRepository;
import io.github.jasper.monitoring.core.port.TransactionWork;
import io.github.jasper.monitoring.core.domain.SecurityAlert;
import io.github.jasper.monitoring.core.domain.AlertDisposition;


import io.github.jasper.monitoring.core.domain.SecurityEvent;
import io.github.jasper.monitoring.core.domain.ControlRecord;
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
 * 线程安全的单元测试仓储实现。
 *
 * <p>该类型仅由测试 classifier 发布，不属于生产制品。</p>
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
    public synchronized <T> T inTransaction(TransactionWork<T> work) {
        List<SecurityEvent> previousEvents = new ArrayList<SecurityEvent>(events);
        Map<String, SecurityAlert> previousAlerts = new LinkedHashMap<String, SecurityAlert>(alerts);
        Map<String, ControlRecord> previousControls = new LinkedHashMap<String, ControlRecord>(controls);
        List<WhitelistEntry> previousWhitelists = new ArrayList<WhitelistEntry>(whitelists);
        Map<String, Set<String>> previousAlertEvents = copySets(alertEvents);
        Map<String, List<AlertDisposition>> previousDispositions = copyLists(alertDispositions);
        try {
            return work.execute();
        } catch (RuntimeException exception) {
            events.clear();
            events.addAll(previousEvents);
            alerts.clear();
            alerts.putAll(previousAlerts);
            controls.clear();
            controls.putAll(previousControls);
            whitelists.clear();
            whitelists.addAll(previousWhitelists);
            alertEvents.clear();
            alertEvents.putAll(previousAlertEvents);
            alertDispositions.clear();
            alertDispositions.putAll(previousDispositions);
            throw exception;
        }
    }

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
        Set<String> ids = alertEvents.computeIfAbsent(alertId,
                k -> new LinkedHashSet<String>());
        ids.add(eventId);
    }
    @Override
    public synchronized void appendAlertDisposition(AlertDisposition disposition) {
        List<AlertDisposition> dispositions = alertDispositions.computeIfAbsent(disposition.getAlertId(),
                k -> new ArrayList<AlertDisposition>());
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
    /** @return 告警摘要的不可变快照，供测试和本地诊断使用 */
    public synchronized List<SecurityAlert> getAlerts() { return Collections.unmodifiableList(new ArrayList<SecurityAlert>(alerts.values())); }
    /** @return 已持久化控制记录的不可变快照，供测试和本地诊断使用 */
    public synchronized List<ControlRecord> getControls() { return Collections.unmodifiableList(new ArrayList<ControlRecord>(controls.values())); }
    /** @return 已持久化事件的不可变快照，供测试和本地诊断使用 */
    public synchronized List<SecurityEvent> getEvents() { return Collections.unmodifiableList(new ArrayList<SecurityEvent>(events)); }

    private static Map<String, Set<String>> copySets(Map<String, Set<String>> source) {
        Map<String, Set<String>> copy = new LinkedHashMap<String, Set<String>>();
        for (Map.Entry<String, Set<String>> entry : source.entrySet()) {
            copy.put(entry.getKey(), new LinkedHashSet<String>(entry.getValue()));
        }
        return copy;
    }

    private static Map<String, List<AlertDisposition>> copyLists(Map<String, List<AlertDisposition>> source) {
        Map<String, List<AlertDisposition>> copy = new LinkedHashMap<String, List<AlertDisposition>>();
        for (Map.Entry<String, List<AlertDisposition>> entry : source.entrySet()) {
            copy.put(entry.getKey(), new ArrayList<AlertDisposition>(entry.getValue()));
        }
        return copy;
    }
}

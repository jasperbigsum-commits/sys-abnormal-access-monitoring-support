package io.github.jasper.monitoring.mybatis.repository;

import io.github.jasper.monitoring.core.domain.AlertDisposition;
import io.github.jasper.monitoring.core.domain.ControlRecord;
import io.github.jasper.monitoring.core.domain.SecurityAlert;
import io.github.jasper.monitoring.core.domain.SecurityEvent;
import io.github.jasper.monitoring.core.domain.WhitelistEntry;
import io.github.jasper.monitoring.core.port.AlertRepository;
import io.github.jasper.monitoring.core.port.ControlRepository;
import io.github.jasper.monitoring.core.port.EventRepository;
import io.github.jasper.monitoring.core.port.MonitoringTransaction;
import io.github.jasper.monitoring.core.port.NotificationDeliveryRepository;
import io.github.jasper.monitoring.core.port.WhitelistRepository;
import io.github.jasper.monitoring.core.port.MonitoringRepository;
import io.github.jasper.monitoring.mybatis.MyBatisMonitoringRepository;
import io.github.jasper.monitoring.mybatis.MyBatisMonitoringRepositoryRegistrar;
import io.github.jasper.monitoring.mybatis.mapper.EventMapper;
import io.github.jasper.monitoring.mybatis.mapper.NotificationDeliveryMapper;
import io.github.jasper.monitoring.mybatis.mapper.AlertMapper;
import io.github.jasper.monitoring.mybatis.po.SecurityAlertPo;
import io.github.jasper.monitoring.mybatis.po.SecurityEventPo;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionManager;

/**
 * The single production persistence adapter. It exposes narrow aggregate ports while retaining
 * the legacy repository only as an internal migration boundary.
 */
public final class MyBatisMonitoringStore implements EventRepository, AlertRepository, ControlRepository,
    WhitelistRepository, NotificationDeliveryRepository, MonitoringTransaction {
    private final MonitoringRepository legacy;
    private final SqlSessionManager sessions;

    public MyBatisMonitoringStore(SqlSessionFactory sqlSessionFactory) {
        Objects.requireNonNull(sqlSessionFactory, "sqlSessionFactory");
        MyBatisMonitoringRepositoryRegistrar.register(sqlSessionFactory);
        this.sessions = SqlSessionManager.newInstance(sqlSessionFactory);
        this.legacy = new MyBatisMonitoringRepository(this.sessions);
    }

    @Override public void save(SecurityEvent event) { legacy.saveEvent(event); }
    @Override public Optional<SecurityEvent> findEvent(String eventId) {
        return read(s -> {
            SecurityEvent event = toEvent(s.getMapper(EventMapper.class).find(eventId));
            return event == null ? Optional.<SecurityEvent>empty() : Optional.of(event);
        });
    }
    @Override public List<SecurityEvent> findSince(String systemId, Instant since) {
        return read(s -> { java.util.ArrayList<SecurityEvent> result = new java.util.ArrayList<SecurityEvent>();
            for (SecurityEventPo row : s.getMapper(EventMapper.class).findSince(systemId, since)) result.add(toEvent(row));
            return result; });
    }
    @Override public void save(SecurityAlert alert) { write(s -> { SecurityAlertPo row = alertRow(alert); if (s.getMapper(AlertMapper.class).update(row) == 0) s.getMapper(AlertMapper.class).insert(row); }); }
    @Override public Optional<SecurityAlert> findAlert(String alertId) { return read(s -> alertOf(s.getMapper(AlertMapper.class).find(alertId))); }
    @Override public Optional<SecurityAlert> findOpen(String fingerprint) { return read(s -> alertOf(s.getMapper(AlertMapper.class).findOpen(fingerprint))); }
    @Override public void linkEvent(String alertId, String eventId) { legacy.linkAlertEvent(alertId, eventId); }
    @Override public void appendDisposition(AlertDisposition disposition) { legacy.appendAlertDisposition(disposition); }
    @Override public List<AlertDisposition> findDispositions(String alertId) { return legacy.findAlertDispositions(alertId); }
    @Override public Optional<ControlRecord> findControl(String idempotencyKey) { return legacy.findControl(idempotencyKey); }
    @Override public void save(ControlRecord record) { legacy.saveControl(record); }
    @Override public boolean isActive(String ruleId, String subject, Instant at) { return legacy.isWhitelisted(ruleId, subject, at); }
    @Override public void add(WhitelistEntry entry) { legacy.addWhitelist(entry); }
    @Override public void record(String deliveryId, String channel, String aggregateId, String status) {
        write(s -> s.getMapper(NotificationDeliveryMapper.class).insert(deliveryId, channel, aggregateId, status));
    }
    @Override public <T> T required(io.github.jasper.monitoring.core.port.TransactionWork<T> work) {
        if (sessions.isManagedSessionStarted()) return work.execute();
        sessions.startManagedSession(false);
        try { T result = work.execute(); sessions.commit(); return result; }
        catch (RuntimeException e) { sessions.rollback(); throw e; }
        finally { sessions.close(); }
    }

    private <T> T read(java.util.function.Function<org.apache.ibatis.session.SqlSession, T> work) {
        boolean owner = !sessions.isManagedSessionStarted();
        if (owner) sessions.startManagedSession(true);
        try { return work.apply(sessions); } finally { if (owner) sessions.close(); }
    }
    private void write(java.util.function.Consumer<org.apache.ibatis.session.SqlSession> work) {
        boolean owner = !sessions.isManagedSessionStarted();
        if (owner) sessions.startManagedSession(false);
        try { work.accept(sessions); if (owner) sessions.commit(); } catch (RuntimeException e) { if (owner) sessions.rollback(); throw e; } finally { if (owner) sessions.close(); }
    }
    private static SecurityEvent toEvent(SecurityEventPo row) {
        if (row == null) return null;
        return SecurityEvent.builder().eventId(row.getEventId()).systemId(row.getSystemId()).eventType(row.getEventType())
            .occurredAt(row.getOccurredAt()).receivedAt(row.getReceivedAt()).userId(row.getUserId()).accountType(row.getAccountType())
            .sourceIp(row.getSourceIp()).requestId(row.getRequestId()).action(row.getAction()).result(row.getResult())
            .dataCount(row.getDataCount()).dataCountKnown(row.isDataCountKnown()).latencyMs(row.getLatencyMs())
            .latencyMsKnown(row.isLatencyMsKnown()).inputStatus(row.getInputStatus()).build();
    }
    private static SecurityAlertPo alertRow(SecurityAlert alert) { SecurityAlertPo row = new SecurityAlertPo(); row.setAlertId(alert.getAlertId()); row.setRuleId(alert.getRuleId()); row.setRiskLevel(alert.getRiskLevel()); row.setFingerprint(alert.getFingerprint()); row.setSubject(alert.getSubject()); row.setStatus(alert.getStatus()); row.setFirstSeen(alert.getFirstSeen()); row.setLastSeen(alert.getLastSeen()); row.setEventCount(alert.getEventCount()); row.setVersion(alert.getVersion()); return row; }
    private static Optional<SecurityAlert> alertOf(SecurityAlertPo row) { return row == null ? Optional.<SecurityAlert>empty() : Optional.of(new SecurityAlert(row.getAlertId(), row.getRuleId(), row.getRiskLevel(), row.getFingerprint(), row.getSubject(), row.getStatus(), row.getFirstSeen(), row.getLastSeen(), row.getEventCount(), row.getVersion())); }
}

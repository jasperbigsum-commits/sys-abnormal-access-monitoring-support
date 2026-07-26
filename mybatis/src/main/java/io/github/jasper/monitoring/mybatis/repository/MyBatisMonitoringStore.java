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
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.apache.ibatis.session.SqlSessionFactory;

/**
 * The single production persistence adapter. It exposes narrow aggregate ports while retaining
 * the legacy repository only as an internal migration boundary.
 */
public final class MyBatisMonitoringStore implements EventRepository, AlertRepository, ControlRepository,
    WhitelistRepository, NotificationDeliveryRepository, MonitoringTransaction {
    private final MonitoringRepository legacy;

    public MyBatisMonitoringStore(SqlSessionFactory sqlSessionFactory) {
        this.legacy = new MyBatisMonitoringRepository(Objects.requireNonNull(sqlSessionFactory, "sqlSessionFactory"));
    }

    @Override public void save(SecurityEvent event) { legacy.saveEvent(event); }
    @Override public Optional<SecurityEvent> findEvent(String eventId) {
        for (SecurityEvent event : legacy.findEventsSince(Instant.EPOCH)) {
            if (event.getEventId().equals(eventId)) return Optional.of(event);
        }
        return Optional.empty();
    }
    @Override public List<SecurityEvent> findSince(String systemId, Instant since) {
        java.util.ArrayList<SecurityEvent> result = new java.util.ArrayList<SecurityEvent>();
        for (SecurityEvent event : legacy.findEventsSince(since)) {
            if (event.getSystemId().equals(systemId)) result.add(event);
        }
        return result;
    }
    @Override public void save(SecurityAlert alert) { legacy.saveAlert(alert); }
    @Override public Optional<SecurityAlert> findAlert(String alertId) { return legacy.findAlert(alertId); }
    @Override public Optional<SecurityAlert> findOpen(String fingerprint) { return legacy.findOpenAlert(fingerprint); }
    @Override public void linkEvent(String alertId, String eventId) { legacy.linkAlertEvent(alertId, eventId); }
    @Override public void appendDisposition(AlertDisposition disposition) { legacy.appendAlertDisposition(disposition); }
    @Override public List<AlertDisposition> findDispositions(String alertId) { return legacy.findAlertDispositions(alertId); }
    @Override public Optional<ControlRecord> findControl(String idempotencyKey) { return legacy.findControl(idempotencyKey); }
    @Override public void save(ControlRecord record) { legacy.saveControl(record); }
    @Override public boolean isActive(String ruleId, String subject, Instant at) { return legacy.isWhitelisted(ruleId, subject, at); }
    @Override public void add(WhitelistEntry entry) { legacy.addWhitelist(entry); }
    @Override public void record(String deliveryId, String channel, String aggregateId, String status) {
        throw new UnsupportedOperationException("Notification delivery persistence is introduced with the delivery schema");
    }
    @Override public <T> T required(io.github.jasper.monitoring.core.port.TransactionWork<T> work) {
        return legacy.inTransaction(work);
    }
}

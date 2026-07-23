package io.github.jasper.monitoring.mybatis;

import io.github.jasper.monitoring.mybatis.po.SecurityEventPo;
import io.github.jasper.monitoring.mybatis.po.SecurityEventAttributePo;
import io.github.jasper.monitoring.mybatis.po.SecurityAlertPo;
import io.github.jasper.monitoring.mybatis.po.ControlActionPo;
import io.github.jasper.monitoring.mybatis.po.AlertDispositionPo;
import io.github.jasper.monitoring.api.ControlStatus;
import io.github.jasper.monitoring.core.domain.AlertDisposition;
import io.github.jasper.monitoring.core.domain.ControlCommand;
import io.github.jasper.monitoring.core.domain.ControlExecution;
import io.github.jasper.monitoring.core.domain.ControlRecord;
import io.github.jasper.monitoring.core.port.MonitoringRepository;
import io.github.jasper.monitoring.core.port.TransactionWork;
import io.github.jasper.monitoring.core.domain.SecurityAlert;
import io.github.jasper.monitoring.core.domain.SecurityEvent;
import io.github.jasper.monitoring.core.domain.WhitelistEntry;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionManager;

/**
 * Production {@link MonitoringRepository} backed by parameterized MyBatis statements.
 *
 * <p>{@link SqlSessionManager} owns the session lifecycle. A repository transaction uses one
 * managed MyBatis session; nested repository calls join it instead of opening independent
 * sessions.</p>
 */
public final class MyBatisMonitoringRepository implements MonitoringRepository {
    private final SqlSessionManager sessionManager;

    /**
     * Creates a repository and registers its mapper and timestamp handler with the supplied factory.
     *
     * @param sqlSessionFactory configured factory connected to a database migrated with {@code monitoring-schema.sql}
     */
    public MyBatisMonitoringRepository(SqlSessionFactory sqlSessionFactory) {
        Objects.requireNonNull(sqlSessionFactory, "sqlSessionFactory");
        MyBatisMonitoringRepositoryRegistrar.register(sqlSessionFactory);
        this.sessionManager = SqlSessionManager.newInstance(sqlSessionFactory);
    }

    @Override
    public <T> T inTransaction(TransactionWork<T> work) {
        Objects.requireNonNull(work, "work");
        if (sessionManager.isManagedSessionStarted()) {
            return work.execute();
        }
        sessionManager.startManagedSession(false);
        try {
            T result = work.execute();
            sessionManager.commit();
            return result;
        } catch (RuntimeException exception) {
            sessionManager.rollback();
            throw exception;
        } finally {
            sessionManager.close();
        }
    }

    @Override
    public void saveEvent(final SecurityEvent event) {
        Objects.requireNonNull(event, "event");
        write(mapper -> {
            mapper.insertEvent(toRow(event));
            for (String roleId : event.getRoleIds()) {
                mapper.insertEventRole(event.getEventId(), roleId);
            }
            for (Map.Entry<String, String> attribute : event.getAttributes().entrySet()) {
                mapper.insertEventAttribute(event.getEventId(), attribute.getKey(), attribute.getValue());
            }
            return null;
        });
    }

    @Override
    public List<SecurityEvent> findEventsSince(final Instant since) {
        Objects.requireNonNull(since, "since");
        return read(mapper -> {
            List<SecurityEvent> events = new ArrayList<SecurityEvent>();
            for (SecurityEventPo row : mapper.findEventsSince(since)) {
                Set<String> roleIds = new LinkedHashSet<String>(mapper.findEventRoles(row.getEventId()));
                Map<String, String> attributes = new LinkedHashMap<String, String>();
                for (SecurityEventAttributePo attribute : mapper.findEventAttributes(row.getEventId())) {
                    attributes.put(attribute.getAttributeKey(), attribute.getAttributeValue());
                }
                events.add(toEvent(row, roleIds, attributes));
            }
            return events;
        });
    }

    @Override
    public Optional<SecurityAlert> findOpenAlert(final String fingerprint) {
        Objects.requireNonNull(fingerprint, "fingerprint");
        SecurityAlertPo row = read(mapper -> mapper.findOpenAlert(fingerprint));
        return row == null ? Optional.<SecurityAlert>empty() : Optional.of(toAlert(row));
    }

    @Override
    public Optional<SecurityAlert> findAlert(final String alertId) {
        Objects.requireNonNull(alertId, "alertId");
        SecurityAlertPo row = read(mapper -> mapper.findAlert(alertId));
        return row == null ? Optional.<SecurityAlert>empty() : Optional.of(toAlert(row));
    }

    @Override
    public void saveAlert(final SecurityAlert alert) {
        Objects.requireNonNull(alert, "alert");
        final SecurityAlertPo row = toRow(alert);
        write(mapper -> {
            if (mapper.updateAlert(row) == 0) {
                mapper.insertAlert(row);
            }
            return null;
        });
    }

    @Override
    public void linkAlertEvent(final String alertId, final String eventId) {
        Objects.requireNonNull(alertId, "alertId");
        Objects.requireNonNull(eventId, "eventId");
        write(mapper -> {
            if (mapper.countAlertEventLink(alertId, eventId) == 0) {
                mapper.insertAlertEventLink(alertId, eventId);
            }
            return null;
        });
    }

    @Override
    public void appendAlertDisposition(final AlertDisposition disposition) {
        Objects.requireNonNull(disposition, "disposition");
        final AlertDispositionPo row = toRow(disposition);
        write(mapper -> {
            mapper.insertAlertDisposition(row);
            return null;
        });
    }

    @Override
    public List<AlertDisposition> findAlertDispositions(final String alertId) {
        Objects.requireNonNull(alertId, "alertId");
        return read(mapper -> {
            List<AlertDisposition> dispositions = new ArrayList<AlertDisposition>();
            for (AlertDispositionPo row : mapper.findAlertDispositions(alertId)) {
                dispositions.add(toDisposition(row));
            }
            return dispositions;
        });
    }

    @Override
    public Optional<ControlRecord> findControl(final String idempotencyKey) {
        Objects.requireNonNull(idempotencyKey, "idempotencyKey");
        ControlActionPo row = read(mapper -> mapper.findControl(idempotencyKey));
        return row == null ? Optional.<ControlRecord>empty() : Optional.of(toControlRecord(row));
    }

    @Override
    public void saveControl(final ControlRecord record) {
        Objects.requireNonNull(record, "record");
        final ControlActionPo row = toRow(record);
        write(mapper -> {
            if (mapper.updateControl(row) == 0) {
                mapper.insertControl(row);
            }
            return null;
        });
    }

    @Override
    public boolean isWhitelisted(final String ruleId, final String subject, final Instant at) {
        Objects.requireNonNull(ruleId, "ruleId");
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(at, "at");
        return read(mapper -> Integer.valueOf(mapper.countActiveWhitelist(ruleId, subject, at))).intValue() > 0;
    }

    @Override
    public void addWhitelist(final WhitelistEntry entry) {
        Objects.requireNonNull(entry, "entry");
        if (entry.getExpiresAt() == null) {
            throw new IllegalArgumentException("Whitelist entries must have an expiry time");
        }
        write(mapper -> {
            if (mapper.countWhitelist(entry.getRuleId(), entry.getSubject(), entry.getExpiresAt()) == 0) {
                mapper.insertWhitelist(entry.getRuleId(), entry.getSubject(), entry.getExpiresAt());
            }
            return null;
        });
    }

    private <T> T read(Function<MonitoringSqlMapper, T> work) {
        if (sessionManager.isManagedSessionStarted()) {
            return work.apply(mapper());
        }
        sessionManager.startManagedSession(true);
        try {
            return work.apply(mapper());
        } finally {
            sessionManager.close();
        }
    }

    private void write(Function<MonitoringSqlMapper, Void> work) {
        inTransaction(() -> work.apply(mapper()));
    }

    private MonitoringSqlMapper mapper() {
        return sessionManager.getMapper(MonitoringSqlMapper.class);
    }

    private static SecurityEventPo toRow(SecurityEvent event) {
        SecurityEventPo row = new SecurityEventPo();
        row.setEventId(event.getEventId());
        row.setSystemId(event.getSystemId());
        row.setEventType(event.getEventType());
        row.setOccurredAt(event.getOccurredAt());
        row.setReceivedAt(event.getReceivedAt());
        row.setUserId(event.getUserId());
        row.setAccountType(event.getAccountType());
        row.setSourceIp(event.getSourceIp());
        row.setDeviceIdHash(event.getDeviceIdHash());
        row.setSessionIdHash(event.getSessionIdHash());
        row.setRequestId(event.getRequestId());
        row.setTraceId(event.getTraceId());
        row.setAction(event.getAction());
        row.setResult(event.getResult());
        row.setReasonCode(event.getReasonCode());
        row.setResourceType(event.getResourceType());
        row.setResourceId(event.getResourceId());
        row.setOrgScope(event.getOrgScope());
        row.setDataCount(event.getDataCount());
        row.setLatencyMs(event.getLatencyMs());
        return row;
    }

    private static SecurityEvent toEvent(SecurityEventPo row, Set<String> roleIds,
                                         Map<String, String> attributes) {
        return SecurityEvent.builder()
            .eventId(row.getEventId())
            .systemId(row.getSystemId())
            .eventType(row.getEventType())
            .occurredAt(row.getOccurredAt())
            .receivedAt(row.getReceivedAt())
            .userId(row.getUserId())
            .accountType(row.getAccountType())
            .roleIds(roleIds)
            .sourceIp(row.getSourceIp())
            .deviceIdHash(row.getDeviceIdHash())
            .sessionIdHash(row.getSessionIdHash())
            .requestId(row.getRequestId())
            .traceId(row.getTraceId())
            .action(row.getAction())
            .result(row.getResult())
            .reasonCode(row.getReasonCode())
            .resourceType(row.getResourceType())
            .resourceId(row.getResourceId())
            .orgScope(row.getOrgScope())
            .dataCount(row.getDataCount())
            .latencyMs(row.getLatencyMs())
            .attributes(attributes)
            .build();
    }

    private static SecurityAlertPo toRow(SecurityAlert alert) {
        SecurityAlertPo row = new SecurityAlertPo();
        row.setAlertId(alert.getAlertId());
        row.setRuleId(alert.getRuleId());
        row.setRiskLevel(alert.getRiskLevel());
        row.setFingerprint(alert.getFingerprint());
        row.setSubject(alert.getSubject());
        row.setStatus(alert.getStatus());
        row.setFirstSeen(alert.getFirstSeen());
        row.setLastSeen(alert.getLastSeen());
        row.setEventCount(alert.getEventCount());
        return row;
    }

    private static SecurityAlert toAlert(SecurityAlertPo row) {
        return new SecurityAlert(row.getAlertId(), row.getRuleId(), row.getRiskLevel(), row.getFingerprint(),
            row.getSubject(), row.getStatus(), row.getFirstSeen(), row.getLastSeen(), row.getEventCount());
    }

    private static AlertDispositionPo toRow(AlertDisposition disposition) {
        AlertDispositionPo row = new AlertDispositionPo();
        row.setDispositionId(disposition.getDispositionId());
        row.setAlertId(disposition.getAlertId());
        row.setDispositionType(disposition.getDispositionType());
        row.setOperatorId(disposition.getOperatorId());
        row.setCommentText(disposition.getCommentText());
        row.setEvidenceSummary(disposition.getEvidenceSummary());
        row.setCreatedAt(disposition.getCreatedAt());
        return row;
    }

    private static AlertDisposition toDisposition(AlertDispositionPo row) {
        return new AlertDisposition(row.getDispositionId(), row.getAlertId(), row.getDispositionType(),
            row.getOperatorId(), row.getCommentText(), row.getEvidenceSummary(), row.getCreatedAt());
    }

    private static ControlActionPo toRow(ControlRecord record) {
        ControlCommand command = record.getCommand();
        ControlExecution execution = record.getExecution();
        ControlActionPo row = new ControlActionPo();
        row.setControlId(execution.getControlId());
        row.setIdempotencyKey(command.getIdempotencyKey());
        row.setAlertId(command.getAlertId());
        row.setSubject(command.getSubject());
        row.setAction(command.getAction());
        row.setExpiresAt(command.getExpiresAt());
        row.setStatus(execution.getStatus());
        row.setFailureReason(execution.getFailureReason());
        row.setExecutedAt(record.getExecutedAt());
        return row;
    }

    private static ControlRecord toControlRecord(ControlActionPo row) {
        ControlCommand command = new ControlCommand(row.getIdempotencyKey(), row.getAlertId(), row.getSubject(),
            row.getAction(), row.getExpiresAt());
        ControlExecution execution;
        if (row.getStatus() == ControlStatus.SUCCEEDED) {
            execution = ControlExecution.succeeded(row.getIdempotencyKey());
        } else if (row.getStatus() == ControlStatus.FAILED) {
            execution = ControlExecution.failed(row.getIdempotencyKey(), row.getFailureReason());
        } else if (row.getStatus() == ControlStatus.SKIPPED) {
            execution = ControlExecution.skipped(row.getIdempotencyKey(), row.getFailureReason());
        } else {
            throw new IllegalStateException("Unknown persisted control status: " + row.getStatus());
        }
        return new ControlRecord(command, execution, row.getExecutedAt());
    }
}

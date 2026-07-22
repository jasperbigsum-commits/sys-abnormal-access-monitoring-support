package io.github.jasper.monitoring.mybatis;

import io.github.jasper.monitoring.api.ControlStatus;
import io.github.jasper.monitoring.core.AlertDisposition;
import io.github.jasper.monitoring.core.ControlCommand;
import io.github.jasper.monitoring.core.ControlExecution;
import io.github.jasper.monitoring.core.ControlRecord;
import io.github.jasper.monitoring.core.MonitoringRepository;
import io.github.jasper.monitoring.core.SecurityAlert;
import io.github.jasper.monitoring.core.SecurityEvent;
import io.github.jasper.monitoring.core.WhitelistEntry;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;

/**
 * Production {@link MonitoringRepository} backed by parameterized MyBatis statements.
 * Each write opens a transaction, commits on success, and rolls back when mapper work fails.
 */
public final class MyBatisMonitoringRepository implements MonitoringRepository {
    private final SqlSessionFactory sqlSessionFactory;

    /**
     * Creates a repository and registers its mapper and timestamp handler with the supplied factory.
     *
     * @param sqlSessionFactory configured factory connected to a database migrated with {@code monitoring-schema.sql}
     */
    public MyBatisMonitoringRepository(SqlSessionFactory sqlSessionFactory) {
        this.sqlSessionFactory = Objects.requireNonNull(sqlSessionFactory, "sqlSessionFactory");
        MyBatisMonitoringRepositoryRegistrar.register(sqlSessionFactory);
    }

    @Override
    public void saveEvent(final SecurityEvent event) {
        Objects.requireNonNull(event, "event");
        write(new MapperWork<Void>() {
            @Override
            public Void execute(MonitoringSqlMapper mapper) {
                mapper.insertEvent(toRow(event));
                for (String roleId : event.getRoleIds()) {
                    mapper.insertEventRole(event.getEventId(), roleId);
                }
                for (Map.Entry<String, String> attribute : event.getAttributes().entrySet()) {
                    mapper.insertEventAttribute(event.getEventId(), attribute.getKey(), attribute.getValue());
                }
                return null;
            }
        });
    }

    @Override
    public List<SecurityEvent> findEventsSince(final Instant since) {
        Objects.requireNonNull(since, "since");
        return read(new MapperWork<List<SecurityEvent>>() {
            @Override
            public List<SecurityEvent> execute(MonitoringSqlMapper mapper) {
                List<SecurityEvent> events = new ArrayList<SecurityEvent>();
                for (MonitoringSqlMapper.EventRow row : mapper.findEventsSince(since)) {
                    Set<String> roleIds = new LinkedHashSet<String>(mapper.findEventRoles(row.getEventId()));
                    Map<String, String> attributes = new LinkedHashMap<String, String>();
                    for (MonitoringSqlMapper.EventAttributeRow attribute : mapper.findEventAttributes(row.getEventId())) {
                        attributes.put(attribute.getAttributeKey(), attribute.getAttributeValue());
                    }
                    events.add(toEvent(row, roleIds, attributes));
                }
                return events;
            }
        });
    }

    @Override
    public Optional<SecurityAlert> findOpenAlert(final String fingerprint) {
        Objects.requireNonNull(fingerprint, "fingerprint");
        MonitoringSqlMapper.AlertRow row = read(new MapperWork<MonitoringSqlMapper.AlertRow>() {
            @Override
            public MonitoringSqlMapper.AlertRow execute(MonitoringSqlMapper mapper) {
                return mapper.findOpenAlert(fingerprint);
            }
        });
        return row == null ? Optional.<SecurityAlert>empty() : Optional.of(toAlert(row));
    }

    @Override
    public Optional<SecurityAlert> findAlert(final String alertId) {
        Objects.requireNonNull(alertId, "alertId");
        MonitoringSqlMapper.AlertRow row = read(new MapperWork<MonitoringSqlMapper.AlertRow>() {
            @Override
            public MonitoringSqlMapper.AlertRow execute(MonitoringSqlMapper mapper) {
                return mapper.findAlert(alertId);
            }
        });
        return row == null ? Optional.<SecurityAlert>empty() : Optional.of(toAlert(row));
    }

    @Override
    public void saveAlert(final SecurityAlert alert) {
        Objects.requireNonNull(alert, "alert");
        final MonitoringSqlMapper.AlertRow row = toRow(alert);
        write(new MapperWork<Void>() {
            @Override
            public Void execute(MonitoringSqlMapper mapper) {
                if (mapper.updateAlert(row) == 0) {
                    mapper.insertAlert(row);
                }
                return null;
            }
        });
    }

    @Override
    public void linkAlertEvent(final String alertId, final String eventId) {
        Objects.requireNonNull(alertId, "alertId");
        Objects.requireNonNull(eventId, "eventId");
        write(new MapperWork<Void>() {
            @Override
            public Void execute(MonitoringSqlMapper mapper) {
                if (mapper.countAlertEventLink(alertId, eventId) == 0) {
                    mapper.insertAlertEventLink(alertId, eventId);
                }
                return null;
            }
        });
    }

    @Override
    public void appendAlertDisposition(final AlertDisposition disposition) {
        Objects.requireNonNull(disposition, "disposition");
        final MonitoringSqlMapper.DispositionRow row = toRow(disposition);
        write(new MapperWork<Void>() {
            @Override
            public Void execute(MonitoringSqlMapper mapper) {
                mapper.insertAlertDisposition(row);
                return null;
            }
        });
    }

    @Override
    public List<AlertDisposition> findAlertDispositions(final String alertId) {
        Objects.requireNonNull(alertId, "alertId");
        return read(new MapperWork<List<AlertDisposition>>() {
            @Override
            public List<AlertDisposition> execute(MonitoringSqlMapper mapper) {
                List<AlertDisposition> dispositions = new ArrayList<AlertDisposition>();
                for (MonitoringSqlMapper.DispositionRow row : mapper.findAlertDispositions(alertId)) {
                    dispositions.add(toDisposition(row));
                }
                return dispositions;
            }
        });
    }

    @Override
    public Optional<ControlRecord> findControl(final String idempotencyKey) {
        Objects.requireNonNull(idempotencyKey, "idempotencyKey");
        MonitoringSqlMapper.ControlRow row = read(new MapperWork<MonitoringSqlMapper.ControlRow>() {
            @Override
            public MonitoringSqlMapper.ControlRow execute(MonitoringSqlMapper mapper) {
                return mapper.findControl(idempotencyKey);
            }
        });
        return row == null ? Optional.<ControlRecord>empty() : Optional.of(toControlRecord(row));
    }

    @Override
    public void saveControl(final ControlRecord record) {
        Objects.requireNonNull(record, "record");
        final MonitoringSqlMapper.ControlRow row = toRow(record);
        write(new MapperWork<Void>() {
            @Override
            public Void execute(MonitoringSqlMapper mapper) {
                if (mapper.updateControl(row) == 0) {
                    mapper.insertControl(row);
                }
                return null;
            }
        });
    }

    @Override
    public boolean isWhitelisted(final String ruleId, final String subject, final Instant at) {
        Objects.requireNonNull(ruleId, "ruleId");
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(at, "at");
        return read(new MapperWork<Integer>() {
            @Override
            public Integer execute(MonitoringSqlMapper mapper) {
                return Integer.valueOf(mapper.countActiveWhitelist(ruleId, subject, at));
            }
        }).intValue() > 0;
    }

    @Override
    public void addWhitelist(final WhitelistEntry entry) {
        Objects.requireNonNull(entry, "entry");
        if (entry.getExpiresAt() == null) {
            throw new IllegalArgumentException("Whitelist entries must have an expiry time");
        }
        write(new MapperWork<Void>() {
            @Override
            public Void execute(MonitoringSqlMapper mapper) {
                if (mapper.countWhitelist(entry.getRuleId(), entry.getSubject(), entry.getExpiresAt()) == 0) {
                    mapper.insertWhitelist(entry.getRuleId(), entry.getSubject(), entry.getExpiresAt());
                }
                return null;
            }
        });
    }

    private <T> T read(MapperWork<T> work) {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            return work.execute(session.getMapper(MonitoringSqlMapper.class));
        }
    }

    private void write(MapperWork<Void> work) {
        try (SqlSession session = sqlSessionFactory.openSession(false)) {
            try {
                work.execute(session.getMapper(MonitoringSqlMapper.class));
                session.commit();
            } catch (RuntimeException exception) {
                session.rollback();
                throw exception;
            }
        }
    }

    private static MonitoringSqlMapper.EventRow toRow(SecurityEvent event) {
        MonitoringSqlMapper.EventRow row = new MonitoringSqlMapper.EventRow();
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

    private static SecurityEvent toEvent(MonitoringSqlMapper.EventRow row, Set<String> roleIds,
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

    private static MonitoringSqlMapper.AlertRow toRow(SecurityAlert alert) {
        MonitoringSqlMapper.AlertRow row = new MonitoringSqlMapper.AlertRow();
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

    private static SecurityAlert toAlert(MonitoringSqlMapper.AlertRow row) {
        return new SecurityAlert(row.getAlertId(), row.getRuleId(), row.getRiskLevel(), row.getFingerprint(),
            row.getSubject(), row.getStatus(), row.getFirstSeen(), row.getLastSeen(), row.getEventCount());
    }

    private static MonitoringSqlMapper.DispositionRow toRow(AlertDisposition disposition) {
        MonitoringSqlMapper.DispositionRow row = new MonitoringSqlMapper.DispositionRow();
        row.setDispositionId(disposition.getDispositionId());
        row.setAlertId(disposition.getAlertId());
        row.setDispositionType(disposition.getDispositionType());
        row.setOperatorId(disposition.getOperatorId());
        row.setCommentText(disposition.getCommentText());
        row.setEvidenceSummary(disposition.getEvidenceSummary());
        row.setCreatedAt(disposition.getCreatedAt());
        return row;
    }

    private static AlertDisposition toDisposition(MonitoringSqlMapper.DispositionRow row) {
        return new AlertDisposition(row.getDispositionId(), row.getAlertId(), row.getDispositionType(),
            row.getOperatorId(), row.getCommentText(), row.getEvidenceSummary(), row.getCreatedAt());
    }

    private static MonitoringSqlMapper.ControlRow toRow(ControlRecord record) {
        ControlCommand command = record.getCommand();
        ControlExecution execution = record.getExecution();
        MonitoringSqlMapper.ControlRow row = new MonitoringSqlMapper.ControlRow();
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

    private static ControlRecord toControlRecord(MonitoringSqlMapper.ControlRow row) {
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

    private interface MapperWork<T> {
        T execute(MonitoringSqlMapper mapper);
    }
}

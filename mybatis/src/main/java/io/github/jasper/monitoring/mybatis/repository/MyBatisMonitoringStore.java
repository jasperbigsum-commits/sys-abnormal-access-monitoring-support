package io.github.jasper.monitoring.mybatis.repository;

import io.github.jasper.monitoring.core.domain.AlertDisposition;
import io.github.jasper.monitoring.core.domain.ControlCommand;
import io.github.jasper.monitoring.core.domain.ControlExecution;
import io.github.jasper.monitoring.core.domain.ControlRecord;
import io.github.jasper.monitoring.core.domain.NotificationDelivery;
import io.github.jasper.monitoring.core.domain.SecurityAlert;
import io.github.jasper.monitoring.core.domain.SecurityEvent;
import io.github.jasper.monitoring.core.domain.WhitelistEntry;
import io.github.jasper.monitoring.api.error.MonitoringErrorCode;
import io.github.jasper.monitoring.api.error.MonitoringPersistenceException;
import io.github.jasper.monitoring.core.port.AlertRepository;
import io.github.jasper.monitoring.core.port.ControlRepository;
import io.github.jasper.monitoring.core.port.EventRepository;
import io.github.jasper.monitoring.core.port.MonitoringTransaction;
import io.github.jasper.monitoring.core.port.ManagementAuditRepository;
import io.github.jasper.monitoring.core.port.ManagementQueryRepository;
import io.github.jasper.monitoring.core.port.NotificationDeliveryRepository;
import io.github.jasper.monitoring.core.port.RuleObservationRepository;
import io.github.jasper.monitoring.core.port.WhitelistRepository;
import io.github.jasper.monitoring.mybatis.MyBatisMonitoringStoreRegistrar;
import io.github.jasper.monitoring.mybatis.mapper.ControlMapper;
import io.github.jasper.monitoring.mybatis.mapper.EventMapper;
import io.github.jasper.monitoring.mybatis.mapper.NotificationDeliveryMapper;
import io.github.jasper.monitoring.mybatis.mapper.RuleObservationMapper;
import io.github.jasper.monitoring.mybatis.mapper.AlertMapper;
import io.github.jasper.monitoring.mybatis.mapper.WhitelistMapper;
import io.github.jasper.monitoring.mybatis.po.AlertDispositionPo;
import io.github.jasper.monitoring.mybatis.po.ControlActionPo;
import io.github.jasper.monitoring.mybatis.po.NotificationDeliveryPo;
import io.github.jasper.monitoring.mybatis.po.SecurityAlertPo;
import io.github.jasper.monitoring.mybatis.po.SecurityEventPo;
import io.github.jasper.monitoring.mybatis.po.SecurityEventInputIssuePo;
import io.github.jasper.monitoring.mybatis.po.SecurityEventFactPo;
import io.github.jasper.monitoring.mybatis.po.RuleObservationPo;
import io.github.jasper.monitoring.core.domain.EventFact;
import io.github.jasper.monitoring.core.domain.rule.RuleObservation;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionManager;
import org.apache.ibatis.exceptions.PersistenceException;

/**
 * The single production persistence adapter exposing narrow, responsibility-specific ports.
 */
public final class MyBatisMonitoringStore implements EventRepository, AlertRepository, ControlRepository,
    WhitelistRepository, NotificationDeliveryRepository, MonitoringTransaction,
    ManagementQueryRepository, ManagementAuditRepository, RuleObservationRepository {
    private final SqlSessionManager sessions;
    private final MyBatisManagementRepository management;

    public MyBatisMonitoringStore(SqlSessionFactory sqlSessionFactory) {
        Objects.requireNonNull(sqlSessionFactory, "sqlSessionFactory");
        MyBatisMonitoringStoreRegistrar.register(sqlSessionFactory);
        this.sessions = SqlSessionManager.newInstance(sqlSessionFactory);
        this.management = new MyBatisManagementRepository(this.sessions);
    }

    @Override public void save(SecurityEvent event) {
        Objects.requireNonNull(event, "event");
        write(s -> {
            EventMapper mapper = s.getMapper(EventMapper.class);
            mapper.insert(eventRow(event));
            for (String roleId : event.getRoleIds()) mapper.insertRole(event.getEventId(), roleId);
            for (java.util.Map.Entry<String, String> attribute : event.getAttributes().entrySet()) {
                mapper.insertAttribute(event.getEventId(), attribute.getKey(), attribute.getValue());
            }
            for (int index = 0; index < event.getInputIssues().size(); index++) {
                mapper.insertInputIssue(inputIssueRow(event.getEventId(), index, event.getInputIssues().get(index)));
            }
            for (EventFact fact : event.getFacts()) mapper.insertFact(factRow(event.getEventId(), fact));
        });
    }
    @Override public Optional<SecurityEvent> findEvent(String eventId) {
        return read(s -> {
            EventMapper mapper = s.getMapper(EventMapper.class);
            SecurityEventPo row = mapper.find(eventId);
            SecurityEvent event = toEvent(row, mapper);
            return event == null ? Optional.<SecurityEvent>empty() : Optional.of(event);
        });
    }
    @Override public List<SecurityEvent> findSince(String systemId, Instant since) {
        return read(s -> { java.util.ArrayList<SecurityEvent> result = new java.util.ArrayList<SecurityEvent>();
            EventMapper mapper = s.getMapper(EventMapper.class);
            for (SecurityEventPo row : mapper.findSince(systemId, since)) result.add(toEvent(row, mapper));
            return result; });
    }
    @Override public void save(SecurityAlert alert) { write(s -> { SecurityAlertPo row = alertRow(alert); if (s.getMapper(AlertMapper.class).update(row) == 0) s.getMapper(AlertMapper.class).insert(row); }); }
    @Override public Optional<SecurityAlert> findAlert(String alertId) { return read(s -> alertOf(s.getMapper(AlertMapper.class).find(alertId))); }
    @Override public Optional<SecurityAlert> findOpen(String fingerprint) { return read(s -> alertOf(s.getMapper(AlertMapper.class).findOpen(fingerprint))); }
    @Override public void linkEvent(String alertId, String eventId) { write(s -> { AlertMapper mapper = s.getMapper(AlertMapper.class); if (mapper.countEventLink(alertId, eventId) == 0) mapper.insertEventLink(alertId, eventId); }); }
    @Override public void appendDisposition(AlertDisposition disposition) { write(s -> s.getMapper(AlertMapper.class).insertDisposition(dispositionRow(disposition))); }
    @Override public List<AlertDisposition> findDispositions(String alertId) { return read(s -> { java.util.ArrayList<AlertDisposition> result = new java.util.ArrayList<AlertDisposition>(); for (AlertDispositionPo row : s.getMapper(AlertMapper.class).findDispositions(alertId)) result.add(dispositionOf(row)); return result; }); }
    @Override public Optional<ControlRecord> findControl(String idempotencyKey) { return read(s -> controlOf(s.getMapper(ControlMapper.class).find(idempotencyKey))); }
    @Override public void save(ControlRecord record) { write(s -> { ControlMapper mapper = s.getMapper(ControlMapper.class); ControlActionPo row = controlRow(record); if (mapper.update(row) == 0) mapper.insert(row); }); }
    @Override public boolean isActive(String systemId, String ruleId, String subject, Instant at) { return read(s -> s.getMapper(WhitelistMapper.class).countActive(systemId, ruleId, subject, at) > 0); }
    @Override public void add(WhitelistEntry entry) { write(s -> s.getMapper(WhitelistMapper.class).insert(
        entry.getWhitelistId() == null ? java.util.UUID.randomUUID().toString() : entry.getWhitelistId(),
        entry.getSystemId(), entry.getRuleId(), entry.getSubject(), entry.getExpiresAt(),
        entry.getApprovedBy(), entry.getReason())); }
    @Override public Optional<NotificationDelivery> find(String channel, String aggregateId) {
        return read(s -> deliveryOf(s.getMapper(NotificationDeliveryMapper.class).find(channel, aggregateId)));
    }
    @Override public boolean create(NotificationDelivery delivery) {
        Objects.requireNonNull(delivery, "delivery");
        try {
            write(s -> s.getMapper(NotificationDeliveryMapper.class).insert(deliveryRow(delivery)));
            return true;
        } catch (MonitoringPersistenceException failure) {
            if (isUniqueViolation(failure)) return false;
            throw failure;
        }
    }
    @Override public boolean update(NotificationDelivery delivery, long expectedVersion) {
        Objects.requireNonNull(delivery, "delivery");
        return writeResult(s -> s.getMapper(NotificationDeliveryMapper.class)
            .update(deliveryRow(delivery), expectedVersion) == 1);
    }
    @Override public List<NotificationDelivery> findDue(String channel, Instant at, int limit) {
        return read(s -> {
            java.util.ArrayList<NotificationDelivery> result = new java.util.ArrayList<NotificationDelivery>();
            for (NotificationDeliveryPo row : s.getMapper(NotificationDeliveryMapper.class)
                .findDue(channel, at, limit)) result.add(deliveryOf(row).get());
            return result;
        });
    }
    @Override public void save(RuleObservation observation) {
        Objects.requireNonNull(observation, "observation");
        write(s -> s.getMapper(RuleObservationMapper.class).insert(observationRow(observation)));
    }
    @Override public <T> T required(io.github.jasper.monitoring.core.port.TransactionWork<T> work) {
        if (sessions.isManagedSessionStarted()) return work.execute();
        sessions.startManagedSession(false);
        try { T result = work.execute(); sessions.commit(); return result; }
        catch (PersistenceException e) { sessions.rollback(); throw persistenceFailure(e); }
        catch (RuntimeException e) { sessions.rollback(); throw e; }
        finally { sessions.close(); }
    }
    @Override public io.github.jasper.monitoring.api.management.ManagementPage<io.github.jasper.monitoring.api.management.model.SecurityEventView> searchEvents(String s,io.github.jasper.monitoring.api.management.query.SecurityEventQuery q){return management.searchEvents(s,q);}
    @Override public Optional<io.github.jasper.monitoring.api.management.model.SecurityEventView> findEventView(String s,String id){return management.findEventView(s,id);}
    @Override public io.github.jasper.monitoring.api.management.ManagementPage<io.github.jasper.monitoring.api.management.model.AlertView> searchAlerts(String s,io.github.jasper.monitoring.api.management.query.AlertQuery q){return management.searchAlerts(s,q);}
    @Override public Optional<io.github.jasper.monitoring.api.management.model.AlertView> findAlertView(String s,String id){return management.findAlertView(s,id);}
    @Override public io.github.jasper.monitoring.api.management.ManagementPage<io.github.jasper.monitoring.api.management.model.AlertAssignmentView> searchAlertAssignments(String s,String id,io.github.jasper.monitoring.api.management.query.AlertAssignmentQuery q){return management.searchAlertAssignments(s,id,q);}
    @Override public boolean transitionAlert(String s,String id,long v,String status,String actor,String reason,String dispositionId){return management.transitionAlert(s,id,v,status,actor,reason,dispositionId);}
    @Override public boolean assignAlert(String s,String id,long v,String actor,String assignee,String reason,String dispositionId){return management.assignAlert(s,id,v,actor,assignee,reason,dispositionId);}
    @Override public Optional<io.github.jasper.monitoring.api.management.model.AlertView> findAlertAssignment(String s,String id,long v,String actor,String assignee,String reason,String dispositionId){return management.findAlertAssignment(s,id,v,actor,assignee,reason,dispositionId);}
    @Override public io.github.jasper.monitoring.api.management.ManagementPage<io.github.jasper.monitoring.api.management.model.RuleView> searchRules(String s,io.github.jasper.monitoring.api.management.query.RuleQuery q){return management.searchRules(s,q);}
    @Override public Optional<io.github.jasper.monitoring.api.management.model.RuleView> findRuleView(String s,String id){return management.findRuleView(s,id);}
    @Override public boolean changeRule(String s,String id,long v,io.github.jasper.monitoring.api.rule.RuleMode mode,long threshold,String actor,String approver,String reason,String key){return management.changeRule(s,id,v,mode,threshold,actor,approver,reason,key);}
    @Override public Optional<io.github.jasper.monitoring.api.management.model.RuleView> findRuleChange(String s,String id,long v,io.github.jasper.monitoring.api.rule.RuleMode mode,long threshold,String actor,String approver,String reason,String key){return management.findRuleChange(s,id,v,mode,threshold,actor,approver,reason,key);}
    @Override public io.github.jasper.monitoring.api.management.ManagementPage<io.github.jasper.monitoring.api.management.model.WhitelistView> searchWhitelists(String s,io.github.jasper.monitoring.api.management.query.WhitelistQuery q){return management.searchWhitelists(s,q);}
    @Override public Optional<io.github.jasper.monitoring.api.management.model.WhitelistView> findWhitelistView(String s,String id){return management.findWhitelistView(s,id);}
    @Override public boolean transitionWhitelist(String s,String id,long v,boolean a,String actor,String reason){return management.transitionWhitelist(s,id,v,a,actor,reason);}
    @Override public io.github.jasper.monitoring.api.management.ManagementPage<io.github.jasper.monitoring.api.management.model.ControlView> searchControls(String s,io.github.jasper.monitoring.api.management.query.ControlQuery q){return management.searchControls(s,q);}
    @Override public Optional<io.github.jasper.monitoring.api.management.model.ControlView> findControlView(String s,String id){return management.findControlView(s,id);}
    @Override public Optional<io.github.jasper.monitoring.core.domain.ControlCommand> findControlCommand(String s,String id){return management.findControlCommand(s,id);}
    @Override public boolean transitionControl(String s,String id,long v,String expected,String target,String reason){return management.transitionControl(s,id,v,expected,target,reason);}
    @Override public void append(io.github.jasper.monitoring.core.domain.management.ManagementAuditRecord record){management.append(record);}

    private <T> T read(java.util.function.Function<org.apache.ibatis.session.SqlSession, T> work) {
        boolean owner = !sessions.isManagedSessionStarted();
        if (owner) sessions.startManagedSession(true);
        try { return work.apply(sessions); }
        catch (PersistenceException e) { throw persistenceFailure(e); }
        finally { if (owner) sessions.close(); }
    }
    private void write(java.util.function.Consumer<org.apache.ibatis.session.SqlSession> work) {
        boolean owner = !sessions.isManagedSessionStarted();
        if (owner) sessions.startManagedSession(false);
        try { work.accept(sessions); if (owner) sessions.commit(); }
        catch (PersistenceException e) { if (owner) sessions.rollback(); throw persistenceFailure(e); }
        catch (RuntimeException e) { if (owner) sessions.rollback(); throw e; }
        finally { if (owner) sessions.close(); }
    }
    private <T> T writeResult(java.util.function.Function<org.apache.ibatis.session.SqlSession, T> work) {
        boolean owner = !sessions.isManagedSessionStarted();
        if (owner) sessions.startManagedSession(false);
        try { T result = work.apply(sessions); if (owner) sessions.commit(); return result; }
        catch (PersistenceException e) { if (owner) sessions.rollback(); throw persistenceFailure(e); }
        catch (RuntimeException e) { if (owner) sessions.rollback(); throw e; }
        finally { if (owner) sessions.close(); }
    }
    private static SecurityEvent toEvent(SecurityEventPo row, EventMapper mapper) {
        if (row == null) return null;
        java.util.Map<String, String> attributes = new java.util.LinkedHashMap<String, String>();
        for (io.github.jasper.monitoring.mybatis.po.SecurityEventAttributePo attribute : mapper.findAttributes(row.getEventId())) attributes.put(attribute.getAttributeKey(), attribute.getAttributeValue());
        java.util.List<String> roles = mapper.findRoles(row.getEventId());
        java.util.List<io.github.jasper.monitoring.api.EventInputIssue> issues = new java.util.ArrayList<io.github.jasper.monitoring.api.EventInputIssue>();
        for (SecurityEventInputIssuePo issue : mapper.findInputIssues(row.getEventId())) {
            issues.add(io.github.jasper.monitoring.api.EventInputIssue.of(issue.getRuleId(), issue.getFactName(), io.github.jasper.monitoring.api.EventInputIssueCode.valueOf(issue.getIssueCode()), io.github.jasper.monitoring.api.EventFactSource.valueOf(issue.getSourceType())));
        }
        return SecurityEvent.builder().eventId(row.getEventId()).systemId(row.getSystemId()).eventType(row.getEventType())
            .occurredAt(row.getOccurredAt()).receivedAt(row.getReceivedAt()).userId(row.getUserId()).accountType(row.getAccountType())
            .sourceIp(row.getSourceIp()).deviceIdHash(row.getDeviceIdHash()).sessionIdHash(row.getSessionIdHash()).attemptedAccountHash(row.getAttemptedAccountHash()).requestId(row.getRequestId()).traceId(row.getTraceId()).action(row.getAction()).result(row.getResult()).reasonCode(row.getReasonCode()).resourceType(row.getResourceType()).resourceId(row.getResourceId()).orgScope(row.getOrgScope())
            .dataCount(row.getDataCount()).dataCountKnown(row.isDataCountKnown()).latencyMs(row.getLatencyMs())
            .latencyMsKnown(row.isLatencyMsKnown()).inputStatus(row.getInputStatus()).roleIds(new java.util.LinkedHashSet<String>(roles)).attributes(attributes).inputIssues(issues).facts(factsOf(mapper.findFacts(row.getEventId()))).build();
    }
    private static SecurityEventPo eventRow(SecurityEvent event) { SecurityEventPo row = new SecurityEventPo(); row.setEventId(event.getEventId()); row.setSystemId(event.getSystemId()); row.setEventType(event.getEventType()); row.setOccurredAt(event.getOccurredAt()); row.setReceivedAt(event.getReceivedAt()); row.setUserId(event.getUserId()); row.setAccountType(event.getAccountType()); row.setSourceIp(event.getSourceIp()); row.setDeviceIdHash(event.getDeviceIdHash()); row.setSessionIdHash(event.getSessionIdHash()); row.setAttemptedAccountHash(event.getAttemptedAccountHash()); row.setRequestId(event.getRequestId()); row.setTraceId(event.getTraceId()); row.setAction(event.getAction()); row.setResult(event.getResult()); row.setReasonCode(event.getReasonCode()); row.setResourceType(event.getResourceType()); row.setResourceId(event.getResourceId()); row.setOrgScope(event.getOrgScope()); row.setDataCount(event.getDataCount()); row.setLatencyMs(event.getLatencyMs()); row.setDataCountKnown(event.hasDataCount()); row.setLatencyMsKnown(event.hasLatencyMs()); row.setInputStatus(event.getInputStatus()); return row; }
    private static SecurityEventInputIssuePo inputIssueRow(String eventId, int index, io.github.jasper.monitoring.api.EventInputIssue issue) { SecurityEventInputIssuePo row = new SecurityEventInputIssuePo(); row.setEventId(eventId); row.setIssueIndex(index); row.setRuleId(issue.getRuleId()); row.setFactName(issue.getFactName()); row.setIssueCode(issue.getIssueCode()); row.setSourceType(issue.getSourceType()); return row; }
    private static SecurityEventFactPo factRow(String eventId, EventFact fact) { SecurityEventFactPo row = new SecurityEventFactPo(); row.setEventId(eventId); row.setFactKey(fact.getKey()); row.setValueType(fact.getValueType()); row.setValueText(fact.getValueText()); row.setSourceType(fact.getSource()); return row; }
    private static RuleObservationPo observationRow(RuleObservation observation) { RuleObservationPo row = new RuleObservationPo(); row.setObservationId(observation.getObservationId()); row.setRuleId(observation.getRuleId()); row.setEventId(observation.getEventId()); row.setSubject(observation.getSubject()); row.setObservedAt(observation.getObservedAt()); return row; }
    private static NotificationDeliveryPo deliveryRow(NotificationDelivery delivery) { NotificationDeliveryPo row = new NotificationDeliveryPo(); row.setDeliveryId(delivery.getDeliveryId()); row.setChannel(delivery.getChannel()); row.setAggregateId(delivery.getAggregateId()); row.setStatus(delivery.getStatus().name()); row.setAttemptCount(delivery.getAttemptCount()); row.setNextAttemptAt(delivery.getNextAttemptAt()); row.setFailureCategory(delivery.getFailureCategory()); row.setUpdatedAt(delivery.getUpdatedAt()); row.setVersion(delivery.getVersion()); return row; }
    private static Optional<NotificationDelivery> deliveryOf(NotificationDeliveryPo row) { return row == null ? Optional.<NotificationDelivery>empty() : Optional.of(new NotificationDelivery(row.getDeliveryId(), row.getChannel(), row.getAggregateId(), NotificationDelivery.Status.valueOf(row.getStatus()), row.getAttemptCount(), row.getNextAttemptAt(), row.getFailureCategory(), row.getUpdatedAt(), row.getVersion())); }
    private static java.util.List<EventFact> factsOf(java.util.List<SecurityEventFactPo> rows) { java.util.List<EventFact> result = new java.util.ArrayList<EventFact>(); for (SecurityEventFactPo row : rows) result.add(new EventFact(row.getFactKey(), row.getValueType(), row.getValueText(), row.getSourceType())); return result; }
    private static SecurityAlertPo alertRow(SecurityAlert alert) { SecurityAlertPo row = new SecurityAlertPo(); row.setAlertId(alert.getAlertId()); row.setRuleId(alert.getRuleId()); row.setRiskLevel(alert.getRiskLevel()); row.setFingerprint(alert.getFingerprint()); row.setSubject(alert.getSubject()); row.setStatus(alert.getStatus()); row.setFirstSeen(alert.getFirstSeen()); row.setLastSeen(alert.getLastSeen()); row.setEventCount(alert.getEventCount()); row.setVersion(alert.getVersion()); return row; }
    private static Optional<SecurityAlert> alertOf(SecurityAlertPo row) { return row == null ? Optional.<SecurityAlert>empty() : Optional.of(new SecurityAlert(row.getAlertId(), row.getRuleId(), row.getRiskLevel(), row.getFingerprint(), row.getSubject(), row.getStatus(), row.getFirstSeen(), row.getLastSeen(), row.getEventCount(), row.getVersion())); }
    private static AlertDispositionPo dispositionRow(AlertDisposition disposition) { AlertDispositionPo row = new AlertDispositionPo(); row.setDispositionId(disposition.getDispositionId()); row.setAlertId(disposition.getAlertId()); row.setDispositionType(disposition.getDispositionType()); row.setOperatorId(disposition.getOperatorId()); row.setCommentText(disposition.getCommentText()); row.setEvidenceSummary(disposition.getEvidenceSummary()); row.setCreatedAt(disposition.getCreatedAt()); return row; }
    private static AlertDisposition dispositionOf(AlertDispositionPo row) { return new AlertDisposition(row.getDispositionId(), row.getAlertId(), row.getDispositionType(), row.getOperatorId(), row.getCommentText(), row.getEvidenceSummary(), row.getCreatedAt()); }
    private static ControlActionPo controlRow(ControlRecord record) { ControlCommand command = record.getCommand(); ControlExecution execution = record.getExecution(); ControlActionPo row = new ControlActionPo(); row.setControlId(execution.getControlId()); row.setSystemId(command.getSystemId()); row.setIdempotencyKey(command.getIdempotencyKey()); row.setAlertId(command.getAlertId()); row.setRuleId(command.getRuleId()); row.setSubject(command.getSubject()); row.setAction(command.getAction()); row.setExpiresAt(command.getExpiresAt()); row.setStatus(execution.getStatus()); row.setFailureReason(execution.getFailureReason()); row.setExecutedAt(record.getExecutedAt()); row.setVersion(0L); return row; }
    private static Optional<ControlRecord> controlOf(ControlActionPo row) { if (row == null) return Optional.empty(); ControlCommand command = new ControlCommand(row.getSystemId(), row.getIdempotencyKey(), row.getAlertId(), row.getSubject(), row.getAction(), row.getExpiresAt(), row.getRuleId()); ControlExecution execution = ControlExecution.restored(row.getControlId(), row.getIdempotencyKey(), row.getStatus(), row.getFailureReason()); return Optional.of(new ControlRecord(command, execution, row.getExecutedAt())); }
    private static MonitoringPersistenceException persistenceFailure(PersistenceException exception) { return new MonitoringPersistenceException(MonitoringErrorCode.PERSISTENCE_OPERATION_FAILED, "Monitoring persistence operation failed", exception); }
    private static boolean isUniqueViolation(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException) {
                SQLException sql = (SQLException) cause;
                if ("23505".equals(sql.getSQLState()) || sql.getErrorCode() == 1062) return true;
            }
        }
        return false;
    }
}

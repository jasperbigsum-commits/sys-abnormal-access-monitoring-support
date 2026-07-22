package io.github.jasper.monitoring.mybatis;

import io.github.jasper.monitoring.api.AccountType;
import io.github.jasper.monitoring.api.AlertStatus;
import io.github.jasper.monitoring.api.ControlActionType;
import io.github.jasper.monitoring.api.ControlStatus;
import io.github.jasper.monitoring.api.DispositionType;
import io.github.jasper.monitoring.api.RiskLevel;
import io.github.jasper.monitoring.api.SecurityEventResult;
import io.github.jasper.monitoring.api.SecurityEventType;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * Internal, parameterized SQL mapper used by {@link MyBatisMonitoringRepository}.
 * Host applications should use {@link MonitoringAdministrationMapper} for management-side statements instead.
 */
interface MonitoringSqlMapper {

    @Insert({
        "INSERT INTO security_event (event_id, system_id, event_type, occurred_at, received_at, user_id, account_type,",
        "source_ip, device_id_hash, session_id_hash, request_id, trace_id, action, result, reason_code, resource_type,",
        "resource_id, org_scope, data_count, latency_ms)",
        "VALUES (#{eventId}, #{systemId}, #{eventType}, #{occurredAt}, #{receivedAt}, #{userId}, #{accountType},",
        "#{sourceIp}, #{deviceIdHash}, #{sessionIdHash}, #{requestId}, #{traceId}, #{action}, #{result}, #{reasonCode},",
        "#{resourceType}, #{resourceId}, #{orgScope}, #{dataCount}, #{latencyMs})"
    })
    int insertEvent(EventRow event);

    @Insert("INSERT INTO security_event_role (event_id, role_id) VALUES (#{eventId}, #{roleId})")
    int insertEventRole(@Param("eventId") String eventId, @Param("roleId") String roleId);

    @Insert({
        "INSERT INTO security_event_attribute (event_id, attribute_key, attribute_value)",
        "VALUES (#{eventId}, #{attributeKey}, #{attributeValue})"
    })
    int insertEventAttribute(@Param("eventId") String eventId, @Param("attributeKey") String attributeKey,
                             @Param("attributeValue") String attributeValue);

    @Select({
        "SELECT event_id AS eventId, system_id AS systemId, event_type AS eventType, occurred_at AS occurredAt,",
        "received_at AS receivedAt, user_id AS userId, account_type AS accountType, source_ip AS sourceIp,",
        "device_id_hash AS deviceIdHash, session_id_hash AS sessionIdHash, request_id AS requestId, trace_id AS traceId,",
        "action, result, reason_code AS reasonCode, resource_type AS resourceType, resource_id AS resourceId,",
        "org_scope AS orgScope, data_count AS dataCount, latency_ms AS latencyMs",
        "FROM security_event WHERE occurred_at >= #{since} ORDER BY occurred_at, event_id"
    })
    List<EventRow> findEventsSince(@Param("since") Instant since);

    @Select("SELECT role_id FROM security_event_role WHERE event_id = #{eventId} ORDER BY role_id")
    List<String> findEventRoles(@Param("eventId") String eventId);

    @Select({
        "SELECT attribute_key AS attributeKey, attribute_value AS attributeValue",
        "FROM security_event_attribute WHERE event_id = #{eventId} ORDER BY attribute_key"
    })
    List<EventAttributeRow> findEventAttributes(@Param("eventId") String eventId);

    @Select({
        "SELECT alert_id AS alertId, rule_id AS ruleId, risk_level AS riskLevel, fingerprint, subject, status,",
        "first_seen AS firstSeen, last_seen AS lastSeen, event_count AS eventCount",
        "FROM security_alert WHERE fingerprint = #{fingerprint}",
        "AND status <> 'CLOSED' AND status <> 'FALSE_POSITIVE'"
    })
    AlertRow findOpenAlert(@Param("fingerprint") String fingerprint);

    @Select({
        "SELECT alert_id AS alertId, rule_id AS ruleId, risk_level AS riskLevel, fingerprint, subject, status,",
        "first_seen AS firstSeen, last_seen AS lastSeen, event_count AS eventCount",
        "FROM security_alert WHERE alert_id = #{alertId}"
    })
    AlertRow findAlert(@Param("alertId") String alertId);

    @Insert({
        "INSERT INTO security_alert (alert_id, rule_id, risk_level, fingerprint, subject, status, first_seen, last_seen, event_count)",
        "VALUES (#{alertId}, #{ruleId}, #{riskLevel}, #{fingerprint}, #{subject}, #{status}, #{firstSeen}, #{lastSeen}, #{eventCount})"
    })
    int insertAlert(AlertRow alert);

    @Update({
        "UPDATE security_alert SET rule_id = #{ruleId}, risk_level = #{riskLevel}, fingerprint = #{fingerprint},",
        "subject = #{subject}, status = #{status}, first_seen = #{firstSeen}, last_seen = #{lastSeen},",
        "event_count = #{eventCount} WHERE alert_id = #{alertId}"
    })
    int updateAlert(AlertRow alert);

    @Select("SELECT COUNT(*) FROM alert_event_link WHERE alert_id = #{alertId} AND event_id = #{eventId}")
    int countAlertEventLink(@Param("alertId") String alertId, @Param("eventId") String eventId);

    @Insert("INSERT INTO alert_event_link (alert_id, event_id) VALUES (#{alertId}, #{eventId})")
    int insertAlertEventLink(@Param("alertId") String alertId, @Param("eventId") String eventId);

    @Insert({
        "INSERT INTO alert_disposition (disposition_id, alert_id, disposition_type, operator_id, comment_text,",
        "evidence_summary, created_at)",
        "VALUES (#{dispositionId}, #{alertId}, #{dispositionType}, #{operatorId}, #{commentText},",
        "#{evidenceSummary}, #{createdAt})"
    })
    int insertAlertDisposition(DispositionRow disposition);

    @Select({
        "SELECT disposition_id AS dispositionId, alert_id AS alertId, disposition_type AS dispositionType,",
        "operator_id AS operatorId, comment_text AS commentText, evidence_summary AS evidenceSummary,",
        "created_at AS createdAt FROM alert_disposition WHERE alert_id = #{alertId}",
        "ORDER BY created_at, disposition_id"
    })
    List<DispositionRow> findAlertDispositions(@Param("alertId") String alertId);

    @Select({
        "SELECT control_id AS controlId, idempotency_key AS idempotencyKey, alert_id AS alertId, subject,",
        "action_type AS action, expires_at AS expiresAt, status, failure_reason AS failureReason, executed_at AS executedAt",
        "FROM control_action WHERE idempotency_key = #{idempotencyKey}"
    })
    ControlRow findControl(@Param("idempotencyKey") String idempotencyKey);

    @Insert({
        "INSERT INTO control_action (control_id, idempotency_key, alert_id, subject, action_type, expires_at, status,",
        "failure_reason, executed_at)",
        "VALUES (#{controlId}, #{idempotencyKey}, #{alertId}, #{subject}, #{action}, #{expiresAt}, #{status},",
        "#{failureReason}, #{executedAt})"
    })
    int insertControl(ControlRow control);

    @Update({
        "UPDATE control_action SET control_id = #{controlId}, alert_id = #{alertId}, subject = #{subject},",
        "action_type = #{action}, expires_at = #{expiresAt}, status = #{status}, failure_reason = #{failureReason},",
        "executed_at = #{executedAt} WHERE idempotency_key = #{idempotencyKey}"
    })
    int updateControl(ControlRow control);

    @Select({
        "SELECT COUNT(*) FROM security_whitelist WHERE rule_id = #{ruleId} AND subject = #{subject}",
        "AND expires_at > #{at}"
    })
    int countActiveWhitelist(@Param("ruleId") String ruleId, @Param("subject") String subject, @Param("at") Instant at);

    @Select({
        "SELECT COUNT(*) FROM security_whitelist WHERE rule_id = #{ruleId} AND subject = #{subject}",
        "AND expires_at = #{expiresAt}"
    })
    int countWhitelist(@Param("ruleId") String ruleId, @Param("subject") String subject,
                       @Param("expiresAt") Instant expiresAt);

    @Insert({
        "INSERT INTO security_whitelist (rule_id, subject, expires_at)",
        "VALUES (#{ruleId}, #{subject}, #{expiresAt})"
    })
    int insertWhitelist(@Param("ruleId") String ruleId, @Param("subject") String subject,
                        @Param("expiresAt") Instant expiresAt);

    final class EventRow {
        private String eventId;
        private String systemId;
        private SecurityEventType eventType;
        private Instant occurredAt;
        private Instant receivedAt;
        private String userId;
        private AccountType accountType;
        private String sourceIp;
        private String deviceIdHash;
        private String sessionIdHash;
        private String requestId;
        private String traceId;
        private String action;
        private SecurityEventResult result;
        private String reasonCode;
        private String resourceType;
        private String resourceId;
        private String orgScope;
        private long dataCount;
        private long latencyMs;

        public String getEventId() { return eventId; }
        public void setEventId(String value) { eventId = value; }
        public String getSystemId() { return systemId; }
        public void setSystemId(String value) { systemId = value; }
        public SecurityEventType getEventType() { return eventType; }
        public void setEventType(SecurityEventType value) { eventType = value; }
        public Instant getOccurredAt() { return occurredAt; }
        public void setOccurredAt(Instant value) { occurredAt = value; }
        public Instant getReceivedAt() { return receivedAt; }
        public void setReceivedAt(Instant value) { receivedAt = value; }
        public String getUserId() { return userId; }
        public void setUserId(String value) { userId = value; }
        public AccountType getAccountType() { return accountType; }
        public void setAccountType(AccountType value) { accountType = value; }
        public String getSourceIp() { return sourceIp; }
        public void setSourceIp(String value) { sourceIp = value; }
        public String getDeviceIdHash() { return deviceIdHash; }
        public void setDeviceIdHash(String value) { deviceIdHash = value; }
        public String getSessionIdHash() { return sessionIdHash; }
        public void setSessionIdHash(String value) { sessionIdHash = value; }
        public String getRequestId() { return requestId; }
        public void setRequestId(String value) { requestId = value; }
        public String getTraceId() { return traceId; }
        public void setTraceId(String value) { traceId = value; }
        public String getAction() { return action; }
        public void setAction(String value) { action = value; }
        public SecurityEventResult getResult() { return result; }
        public void setResult(SecurityEventResult value) { result = value; }
        public String getReasonCode() { return reasonCode; }
        public void setReasonCode(String value) { reasonCode = value; }
        public String getResourceType() { return resourceType; }
        public void setResourceType(String value) { resourceType = value; }
        public String getResourceId() { return resourceId; }
        public void setResourceId(String value) { resourceId = value; }
        public String getOrgScope() { return orgScope; }
        public void setOrgScope(String value) { orgScope = value; }
        public long getDataCount() { return dataCount; }
        public void setDataCount(long value) { dataCount = value; }
        public long getLatencyMs() { return latencyMs; }
        public void setLatencyMs(long value) { latencyMs = value; }
    }

    final class EventAttributeRow {
        private String attributeKey;
        private String attributeValue;
        public String getAttributeKey() { return attributeKey; }
        public void setAttributeKey(String value) { attributeKey = value; }
        public String getAttributeValue() { return attributeValue; }
        public void setAttributeValue(String value) { attributeValue = value; }
    }

    final class AlertRow {
        private String alertId;
        private String ruleId;
        private RiskLevel riskLevel;
        private String fingerprint;
        private String subject;
        private AlertStatus status;
        private Instant firstSeen;
        private Instant lastSeen;
        private int eventCount;
        public String getAlertId() { return alertId; }
        public void setAlertId(String value) { alertId = value; }
        public String getRuleId() { return ruleId; }
        public void setRuleId(String value) { ruleId = value; }
        public RiskLevel getRiskLevel() { return riskLevel; }
        public void setRiskLevel(RiskLevel value) { riskLevel = value; }
        public String getFingerprint() { return fingerprint; }
        public void setFingerprint(String value) { fingerprint = value; }
        public String getSubject() { return subject; }
        public void setSubject(String value) { subject = value; }
        public AlertStatus getStatus() { return status; }
        public void setStatus(AlertStatus value) { status = value; }
        public Instant getFirstSeen() { return firstSeen; }
        public void setFirstSeen(Instant value) { firstSeen = value; }
        public Instant getLastSeen() { return lastSeen; }
        public void setLastSeen(Instant value) { lastSeen = value; }
        public int getEventCount() { return eventCount; }
        public void setEventCount(int value) { eventCount = value; }
    }

    final class DispositionRow {
        private String dispositionId;
        private String alertId;
        private DispositionType dispositionType;
        private String operatorId;
        private String commentText;
        private String evidenceSummary;
        private Instant createdAt;
        public String getDispositionId() { return dispositionId; }
        public void setDispositionId(String value) { dispositionId = value; }
        public String getAlertId() { return alertId; }
        public void setAlertId(String value) { alertId = value; }
        public DispositionType getDispositionType() { return dispositionType; }
        public void setDispositionType(DispositionType value) { dispositionType = value; }
        public String getOperatorId() { return operatorId; }
        public void setOperatorId(String value) { operatorId = value; }
        public String getCommentText() { return commentText; }
        public void setCommentText(String value) { commentText = value; }
        public String getEvidenceSummary() { return evidenceSummary; }
        public void setEvidenceSummary(String value) { evidenceSummary = value; }
        public Instant getCreatedAt() { return createdAt; }
        public void setCreatedAt(Instant value) { createdAt = value; }
    }

    final class ControlRow {
        private String controlId;
        private String idempotencyKey;
        private String alertId;
        private String subject;
        private ControlActionType action;
        private Instant expiresAt;
        private ControlStatus status;
        private String failureReason;
        private Instant executedAt;
        public String getControlId() { return controlId; }
        public void setControlId(String value) { controlId = value; }
        public String getIdempotencyKey() { return idempotencyKey; }
        public void setIdempotencyKey(String value) { idempotencyKey = value; }
        public String getAlertId() { return alertId; }
        public void setAlertId(String value) { alertId = value; }
        public String getSubject() { return subject; }
        public void setSubject(String value) { subject = value; }
        public ControlActionType getAction() { return action; }
        public void setAction(ControlActionType value) { action = value; }
        public Instant getExpiresAt() { return expiresAt; }
        public void setExpiresAt(Instant value) { expiresAt = value; }
        public ControlStatus getStatus() { return status; }
        public void setStatus(ControlStatus value) { status = value; }
        public String getFailureReason() { return failureReason; }
        public void setFailureReason(String value) { failureReason = value; }
        public Instant getExecutedAt() { return executedAt; }
        public void setExecutedAt(Instant value) { executedAt = value; }
    }
}

package io.github.jasper.monitoring.mybatis;

import io.github.jasper.monitoring.mybatis.po.AlertDispositionPo;
import io.github.jasper.monitoring.mybatis.po.ControlActionPo;
import io.github.jasper.monitoring.mybatis.po.SecurityAlertPo;
import io.github.jasper.monitoring.mybatis.po.SecurityEventAttributePo;
import io.github.jasper.monitoring.mybatis.po.SecurityEventInputIssuePo;
import io.github.jasper.monitoring.mybatis.po.SecurityEventPo;
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
        "resource_id, org_scope, data_count, latency_ms, data_count_known, latency_ms_known, input_status)",
        "VALUES (#{eventId}, #{systemId}, #{eventType}, #{occurredAt}, #{receivedAt}, #{userId}, #{accountType},",
        "#{sourceIp}, #{deviceIdHash}, #{sessionIdHash}, #{requestId}, #{traceId}, #{action}, #{result}, #{reasonCode},",
        "#{resourceType}, #{resourceId}, #{orgScope}, #{dataCount}, #{latencyMs}, #{dataCountKnown},",
        "#{latencyMsKnown}, #{inputStatus})"
    })
    int insertEvent(SecurityEventPo event);

    @Insert("INSERT INTO security_event_role (event_id, role_id) VALUES (#{eventId}, #{roleId})")
    int insertEventRole(@Param("eventId") String eventId, @Param("roleId") String roleId);

    @Insert({
        "INSERT INTO security_event_attribute (event_id, attribute_key, attribute_value)",
        "VALUES (#{eventId}, #{attributeKey}, #{attributeValue})"
    })
    int insertEventAttribute(@Param("eventId") String eventId, @Param("attributeKey") String attributeKey,
                             @Param("attributeValue") String attributeValue);

    @Insert({
        "INSERT INTO security_event_input_issue (event_id, issue_index, rule_id, fact_name, issue_code, source_type)",
        "VALUES (#{eventId}, #{issueIndex}, #{ruleId}, #{factName}, #{issueCode}, #{sourceType})"
    })
    int insertEventInputIssue(SecurityEventInputIssuePo issue);

    @Select({
        "SELECT event_id AS eventId, system_id AS systemId, event_type AS eventType, occurred_at AS occurredAt,",
        "received_at AS receivedAt, user_id AS userId, account_type AS accountType, source_ip AS sourceIp,",
        "device_id_hash AS deviceIdHash, session_id_hash AS sessionIdHash, request_id AS requestId, trace_id AS traceId,",
        "action, result, reason_code AS reasonCode, resource_type AS resourceType, resource_id AS resourceId,",
        "org_scope AS orgScope, data_count AS dataCount, latency_ms AS latencyMs,",
        "data_count_known AS dataCountKnown, latency_ms_known AS latencyMsKnown, input_status AS inputStatus",
        "FROM security_event WHERE occurred_at >= #{since} ORDER BY occurred_at, event_id"
    })
    List<SecurityEventPo> findEventsSince(@Param("since") Instant since);

    @Select("SELECT role_id FROM security_event_role WHERE event_id = #{eventId} ORDER BY role_id")
    List<String> findEventRoles(@Param("eventId") String eventId);

    @Select({
        "SELECT attribute_key AS attributeKey, attribute_value AS attributeValue",
        "FROM security_event_attribute WHERE event_id = #{eventId} ORDER BY attribute_key"
    })
    List<SecurityEventAttributePo> findEventAttributes(@Param("eventId") String eventId);

    @Select({
        "SELECT event_id AS eventId, issue_index AS issueIndex, rule_id AS ruleId, fact_name AS factName,",
        "issue_code AS issueCode, source_type AS sourceType",
        "FROM security_event_input_issue WHERE event_id = #{eventId} ORDER BY issue_index"
    })
    List<SecurityEventInputIssuePo> findEventInputIssues(@Param("eventId") String eventId);

    @Select({
        "SELECT alert_id AS alertId, rule_id AS ruleId, risk_level AS riskLevel, fingerprint, subject, status,",
        "first_seen AS firstSeen, last_seen AS lastSeen, event_count AS eventCount",
        "FROM security_alert WHERE fingerprint = #{fingerprint}",
        "AND status <> 'CLOSED' AND status <> 'FALSE_POSITIVE'"
    })
    SecurityAlertPo findOpenAlert(@Param("fingerprint") String fingerprint);

    @Select({
        "SELECT alert_id AS alertId, rule_id AS ruleId, risk_level AS riskLevel, fingerprint, subject, status,",
        "first_seen AS firstSeen, last_seen AS lastSeen, event_count AS eventCount",
        "FROM security_alert WHERE alert_id = #{alertId}"
    })
    SecurityAlertPo findAlert(@Param("alertId") String alertId);

    @Insert({
        "INSERT INTO security_alert (alert_id, rule_id, risk_level, fingerprint, subject, status, first_seen, last_seen, event_count)",
        "VALUES (#{alertId}, #{ruleId}, #{riskLevel}, #{fingerprint}, #{subject}, #{status}, #{firstSeen}, #{lastSeen}, #{eventCount})"
    })
    int insertAlert(SecurityAlertPo alert);

    @Update({
        "UPDATE security_alert SET rule_id = #{ruleId}, risk_level = #{riskLevel}, fingerprint = #{fingerprint},",
        "subject = #{subject}, status = #{status}, first_seen = #{firstSeen}, last_seen = #{lastSeen},",
        "event_count = #{eventCount} WHERE alert_id = #{alertId}"
    })
    int updateAlert(SecurityAlertPo alert);

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
    int insertAlertDisposition(AlertDispositionPo disposition);

    @Select({
        "SELECT disposition_id AS dispositionId, alert_id AS alertId, disposition_type AS dispositionType,",
        "operator_id AS operatorId, comment_text AS commentText, evidence_summary AS evidenceSummary,",
        "created_at AS createdAt FROM alert_disposition WHERE alert_id = #{alertId}",
        "ORDER BY created_at, disposition_id"
    })
    List<AlertDispositionPo> findAlertDispositions(@Param("alertId") String alertId);

    @Select({
        "SELECT control_id AS controlId, idempotency_key AS idempotencyKey, alert_id AS alertId, rule_id AS ruleId, subject,",
        "action_type AS action, expires_at AS expiresAt, status, failure_reason AS failureReason, executed_at AS executedAt",
        "FROM control_action WHERE idempotency_key = #{idempotencyKey}"
    })
    ControlActionPo findControl(@Param("idempotencyKey") String idempotencyKey);

    @Insert({
        "INSERT INTO control_action (control_id, idempotency_key, alert_id, rule_id, subject, action_type, expires_at, status,",
        "failure_reason, executed_at)",
        "VALUES (#{controlId}, #{idempotencyKey}, #{alertId}, #{ruleId}, #{subject}, #{action}, #{expiresAt}, #{status},",
        "#{failureReason}, #{executedAt})"
    })
    int insertControl(ControlActionPo control);

    @Update({
        "UPDATE control_action SET control_id = #{controlId}, alert_id = #{alertId}, rule_id = #{ruleId}, subject = #{subject},",
        "action_type = #{action}, expires_at = #{expiresAt}, status = #{status}, failure_reason = #{failureReason},",
        "executed_at = #{executedAt} WHERE idempotency_key = #{idempotencyKey}"
    })
    int updateControl(ControlActionPo control);

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

}

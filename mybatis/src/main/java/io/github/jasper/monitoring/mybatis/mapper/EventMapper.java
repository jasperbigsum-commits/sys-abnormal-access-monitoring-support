package io.github.jasper.monitoring.mybatis.mapper;

import io.github.jasper.monitoring.mybatis.po.SecurityEventPo;
import io.github.jasper.monitoring.mybatis.po.SecurityEventAttributePo;
import io.github.jasper.monitoring.mybatis.po.SecurityEventInputIssuePo;
import io.github.jasper.monitoring.mybatis.po.SecurityEventFactPo;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;

/** Explicit event SQL boundary. Implementations are supplied by MyBatis configuration. */
public interface EventMapper {
    @Insert("INSERT INTO monitoring_security_event (event_id, system_id, event_type, occurred_at, received_at, user_id, account_type, source_ip, device_id_hash, session_id_hash, attempted_account_hash, request_id, trace_id, action, result, reason_code, resource_type, resource_id, org_scope, data_count, latency_ms, data_count_known, latency_ms_known, input_status) VALUES (#{eventId}, #{systemId}, #{eventType}, #{occurredAt}, #{receivedAt}, #{userId}, #{accountType}, #{sourceIp}, #{deviceIdHash}, #{sessionIdHash}, #{attemptedAccountHash}, #{requestId}, #{traceId}, #{action}, #{result}, #{reasonCode}, #{resourceType}, #{resourceId}, #{orgScope}, #{dataCount}, #{latencyMs}, #{dataCountKnown}, #{latencyMsKnown}, #{inputStatus})")
    int insert(SecurityEventPo event);
    @Insert("INSERT INTO monitoring_security_event_role (event_id, role_id) VALUES (#{eventId}, #{roleId})")
    int insertRole(@Param("eventId") String eventId, @Param("roleId") String roleId);
    @Insert("INSERT INTO monitoring_security_event_attribute (event_id, attribute_key, attribute_value) VALUES (#{eventId}, #{attributeKey}, #{attributeValue})")
    int insertAttribute(@Param("eventId") String eventId, @Param("attributeKey") String attributeKey,
                        @Param("attributeValue") String attributeValue);
    @Insert("INSERT INTO monitoring_security_event_input_issue (event_id, issue_index, rule_id, fact_name, issue_code, source_type) VALUES (#{eventId}, #{issueIndex}, #{ruleId}, #{factName}, #{issueCode}, #{sourceType})")
    int insertInputIssue(SecurityEventInputIssuePo issue);
    @Insert("INSERT INTO monitoring_security_event_fact (event_id, fact_key, value_type, value_text, source_type) VALUES (#{eventId}, #{factKey}, #{valueType}, #{valueText}, #{sourceType})")
    int insertFact(SecurityEventFactPo fact);
    @Select("SELECT event_id AS eventId, system_id AS systemId, event_type AS eventType, occurred_at AS occurredAt, received_at AS receivedAt, user_id AS userId, account_type AS accountType, source_ip AS sourceIp, device_id_hash AS deviceIdHash, session_id_hash AS sessionIdHash, attempted_account_hash AS attemptedAccountHash, request_id AS requestId, trace_id AS traceId, action, result, reason_code AS reasonCode, resource_type AS resourceType, resource_id AS resourceId, org_scope AS orgScope, data_count AS dataCount, latency_ms AS latencyMs, data_count_known AS dataCountKnown, latency_ms_known AS latencyMsKnown, input_status AS inputStatus FROM monitoring_security_event WHERE event_id = #{eventId}")
    SecurityEventPo find(@Param("eventId") String eventId);
    @Select("SELECT event_id AS eventId, fact_key AS factKey, value_type AS valueType, value_text AS valueText, source_type AS sourceType FROM monitoring_security_event_fact WHERE event_id = #{eventId} ORDER BY fact_key")
    List<SecurityEventFactPo> findFacts(@Param("eventId") String eventId);
    @Select("SELECT event_id AS eventId, system_id AS systemId, event_type AS eventType, occurred_at AS occurredAt, received_at AS receivedAt, user_id AS userId, account_type AS accountType, source_ip AS sourceIp, device_id_hash AS deviceIdHash, session_id_hash AS sessionIdHash, attempted_account_hash AS attemptedAccountHash, request_id AS requestId, trace_id AS traceId, action, result, reason_code AS reasonCode, resource_type AS resourceType, resource_id AS resourceId, org_scope AS orgScope, data_count AS dataCount, latency_ms AS latencyMs, data_count_known AS dataCountKnown, latency_ms_known AS latencyMsKnown, input_status AS inputStatus FROM monitoring_security_event WHERE system_id = #{systemId} AND occurred_at >= #{since} ORDER BY occurred_at, event_id")
    List<SecurityEventPo> findSince(@Param("systemId") String systemId, @Param("since") Instant since);
    @Select("SELECT role_id FROM monitoring_security_event_role WHERE event_id = #{eventId} ORDER BY role_id")
    List<String> findRoles(@Param("eventId") String eventId);
    @Select("SELECT attribute_key AS attributeKey, attribute_value AS attributeValue FROM monitoring_security_event_attribute WHERE event_id = #{eventId} ORDER BY attribute_key")
    List<SecurityEventAttributePo> findAttributes(@Param("eventId") String eventId);
    @Select("SELECT event_id AS eventId, issue_index AS issueIndex, rule_id AS ruleId, fact_name AS factName, issue_code AS issueCode, source_type AS sourceType FROM monitoring_security_event_input_issue WHERE event_id = #{eventId} ORDER BY issue_index")
    List<SecurityEventInputIssuePo> findInputIssues(@Param("eventId") String eventId);
}

package io.github.jasper.monitoring.mybatis.mapper;

import io.github.jasper.monitoring.mybatis.po.SecurityEventPo;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;

/** Explicit event SQL boundary. Implementations are supplied by MyBatis configuration. */
public interface EventMapper {
    @Insert("INSERT INTO security_event (event_id, system_id, event_type, occurred_at, received_at, user_id, account_type, source_ip, device_id_hash, session_id_hash, request_id, trace_id, action, result, reason_code, resource_type, resource_id, org_scope, data_count, latency_ms, data_count_known, latency_ms_known, input_status) VALUES (#{eventId}, #{systemId}, #{eventType}, #{occurredAt}, #{receivedAt}, #{userId}, #{accountType}, #{sourceIp}, #{deviceIdHash}, #{sessionIdHash}, #{requestId}, #{traceId}, #{action}, #{result}, #{reasonCode}, #{resourceType}, #{resourceId}, #{orgScope}, #{dataCount}, #{latencyMs}, #{dataCountKnown}, #{latencyMsKnown}, #{inputStatus})")
    int insert(SecurityEventPo event);
    @Select("SELECT event_id AS eventId, system_id AS systemId, event_type AS eventType, occurred_at AS occurredAt, received_at AS receivedAt, user_id AS userId, account_type AS accountType, source_ip AS sourceIp, device_id_hash AS deviceIdHash, session_id_hash AS sessionIdHash, request_id AS requestId, trace_id AS traceId, action, result, reason_code AS reasonCode, resource_type AS resourceType, resource_id AS resourceId, org_scope AS orgScope, data_count AS dataCount, latency_ms AS latencyMs, data_count_known AS dataCountKnown, latency_ms_known AS latencyMsKnown, input_status AS inputStatus FROM security_event WHERE event_id = #{eventId}")
    SecurityEventPo find(@Param("eventId") String eventId);
    @Select("SELECT event_id AS eventId, system_id AS systemId, event_type AS eventType, occurred_at AS occurredAt, received_at AS receivedAt, user_id AS userId, account_type AS accountType, source_ip AS sourceIp, device_id_hash AS deviceIdHash, session_id_hash AS sessionIdHash, request_id AS requestId, trace_id AS traceId, action, result, reason_code AS reasonCode, resource_type AS resourceType, resource_id AS resourceId, org_scope AS orgScope, data_count AS dataCount, latency_ms AS latencyMs, data_count_known AS dataCountKnown, latency_ms_known AS latencyMsKnown, input_status AS inputStatus FROM security_event WHERE system_id = #{systemId} AND occurred_at >= #{since} ORDER BY occurred_at, event_id")
    List<SecurityEventPo> findSince(@Param("systemId") String systemId, @Param("since") Instant since);
}

package io.github.jasper.monitoring.mybatis.mapper;

import io.github.jasper.monitoring.mybatis.po.ControlActionPo;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import java.time.Instant;
import java.util.List;

/** Explicit control SQL boundary. */
public interface ControlMapper {
    @Select("SELECT control_id AS controlId, system_id AS systemId, idempotency_key AS idempotencyKey, alert_id AS alertId, rule_id AS ruleId, subject, action_type AS action, expires_at AS expiresAt, status, failure_reason AS failureReason, executed_at AS executedAt, version FROM monitoring_control_action WHERE idempotency_key = #{idempotencyKey}")
    ControlActionPo find(@Param("idempotencyKey") String idempotencyKey);
    @Select("SELECT control_id AS controlId, system_id AS systemId, idempotency_key AS idempotencyKey, alert_id AS alertId, rule_id AS ruleId, subject, action_type AS action, expires_at AS expiresAt, status, failure_reason AS failureReason, executed_at AS executedAt, version FROM monitoring_control_action WHERE system_id = #{systemId} AND subject = #{subject} AND status = 'SUCCEEDED' AND expires_at > #{at} ORDER BY executed_at, control_id")
    List<ControlActionPo> findActive(@Param("systemId") String systemId, @Param("subject") String subject,
                                     @Param("at") Instant at);
    @Insert("INSERT INTO monitoring_control_action (control_id, system_id, idempotency_key, alert_id, rule_id, subject, action_type, expires_at, status, failure_reason, executed_at, version) VALUES (#{controlId}, #{systemId}, #{idempotencyKey}, #{alertId}, #{ruleId}, #{subject}, #{action}, #{expiresAt}, #{status}, #{failureReason}, #{executedAt}, #{version})")
    int insert(ControlActionPo control);
    @Update("UPDATE monitoring_control_action SET status = #{status}, failure_reason = #{failureReason}, executed_at = #{executedAt}, version = version + 1 WHERE idempotency_key = #{idempotencyKey} AND version = #{version}")
    int update(ControlActionPo control);

    @Insert("INSERT INTO monitoring_control_action (control_id, system_id, idempotency_key, alert_id, rule_id, subject, action_type, expires_at, status, failure_reason, executed_at, version) VALUES (#{controlId}, #{systemId}, #{idempotencyKey}, #{alertId}, #{ruleId}, #{subject}, #{action}, #{expiresAt}, 'PENDING', NULL, #{executedAt}, 0)")
    int reserve(@Param("controlId") String controlId, @Param("idempotencyKey") String idempotencyKey,
                @Param("systemId") String systemId,
                @Param("alertId") String alertId, @Param("ruleId") String ruleId,
                @Param("subject") String subject, @Param("action") String action,
                @Param("expiresAt") Instant expiresAt,
                @Param("executedAt") java.time.Instant executedAt);

    @Insert("INSERT INTO monitoring_control_action_attempt (control_id, attempt_no, status, failure_reason, attempted_at) VALUES (#{controlId}, #{attemptNo}, #{status}, #{failureReason}, #{attemptedAt})")
    int appendAttempt(@Param("controlId") String controlId, @Param("attemptNo") int attemptNo,
                      @Param("status") String status, @Param("failureReason") String failureReason,
                      @Param("attemptedAt") java.time.Instant attemptedAt);

    @Update("UPDATE monitoring_control_action SET status = #{targetStatus}, failure_reason = #{failureReason}, executed_at = #{executedAt}, version = version + 1 WHERE idempotency_key = #{idempotencyKey} AND status = #{expectedStatus} AND version = #{expectedVersion}")
    int transition(@Param("idempotencyKey") String idempotencyKey, @Param("expectedVersion") long expectedVersion,
                   @Param("expectedStatus") String expectedStatus, @Param("targetStatus") String targetStatus,
                   @Param("failureReason") String failureReason, @Param("executedAt") java.time.Instant executedAt);

    @Select("SELECT COALESCE(MAX(attempt_no), 0) FROM monitoring_control_action_attempt WHERE control_id = #{controlId}")
    int maxAttempt(@Param("controlId") String controlId);
}

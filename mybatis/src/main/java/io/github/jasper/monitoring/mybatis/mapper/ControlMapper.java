package io.github.jasper.monitoring.mybatis.mapper;

import io.github.jasper.monitoring.mybatis.po.ControlActionPo;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** Explicit control SQL boundary. */
public interface ControlMapper {
    @Select("SELECT control_id AS controlId, idempotency_key AS idempotencyKey, alert_id AS alertId, rule_id AS ruleId, subject, action_type AS action, expires_at AS expiresAt, status, failure_reason AS failureReason, executed_at AS executedAt, version FROM control_action WHERE idempotency_key = #{idempotencyKey}")
    ControlActionPo find(@Param("idempotencyKey") String idempotencyKey);
    @Insert("INSERT INTO control_action (control_id, idempotency_key, alert_id, rule_id, subject, action_type, expires_at, status, failure_reason, executed_at, version) VALUES (#{controlId}, #{idempotencyKey}, #{alertId}, #{ruleId}, #{subject}, #{action}, #{expiresAt}, #{status}, #{failureReason}, #{executedAt}, #{version})")
    int insert(ControlActionPo control);
    @Update("UPDATE control_action SET status = #{status}, failure_reason = #{failureReason}, executed_at = #{executedAt}, version = version + 1 WHERE idempotency_key = #{idempotencyKey} AND version = #{version}")
    int update(ControlActionPo control);

    @Insert("INSERT INTO control_action (control_id, idempotency_key, alert_id, rule_id, subject, action_type, expires_at, status, failure_reason, executed_at, version) VALUES (#{controlId}, #{idempotencyKey}, #{alertId}, #{ruleId}, #{subject}, #{action}, NULL, 'PENDING', NULL, #{executedAt}, 0)")
    int reserve(@Param("controlId") String controlId, @Param("idempotencyKey") String idempotencyKey,
                @Param("alertId") String alertId, @Param("ruleId") String ruleId,
                @Param("subject") String subject, @Param("action") String action,
                @Param("executedAt") java.time.Instant executedAt);

    @Insert("INSERT INTO control_action_attempt (control_id, attempt_no, status, failure_reason, attempted_at) VALUES (#{controlId}, #{attemptNo}, #{status}, #{failureReason}, #{attemptedAt})")
    int appendAttempt(@Param("controlId") String controlId, @Param("attemptNo") int attemptNo,
                      @Param("status") String status, @Param("failureReason") String failureReason,
                      @Param("attemptedAt") java.time.Instant attemptedAt);

    @Update("UPDATE control_action SET status = #{status}, failure_reason = #{failureReason}, executed_at = #{executedAt}, version = version + 1 WHERE idempotency_key = #{idempotencyKey} AND status = 'PENDING'")
    int completeReservation(@Param("idempotencyKey") String idempotencyKey, @Param("status") String status,
                            @Param("failureReason") String failureReason,
                            @Param("executedAt") java.time.Instant executedAt);
}

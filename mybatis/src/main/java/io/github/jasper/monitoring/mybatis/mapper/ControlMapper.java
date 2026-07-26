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
    @Insert("INSERT INTO control_action (control_id, idempotency_key, subject, action_type, status, executed_at, version) VALUES (#{controlId}, #{idempotencyKey}, #{subject}, #{action}, #{status}, #{executedAt}, #{version})")
    int insert(ControlActionPo control);
    @Update("UPDATE control_action SET status = #{status}, failure_reason = #{failureReason}, executed_at = #{executedAt}, version = version + 1 WHERE idempotency_key = #{idempotencyKey} AND version = #{version}")
    int update(ControlActionPo control);
}

package io.github.jasper.monitoring.mybatis.mapper;

import io.github.jasper.monitoring.mybatis.po.SecurityAlertPo;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** Explicit alert SQL boundary. */
public interface AlertMapper {
    @Insert("INSERT INTO security_alert (alert_id, rule_id, risk_level, fingerprint, subject, status, first_seen, last_seen, event_count, version) VALUES (#{alertId}, #{ruleId}, #{riskLevel}, #{fingerprint}, #{subject}, #{status}, #{firstSeen}, #{lastSeen}, #{eventCount}, #{version})")
    int insert(SecurityAlertPo alert);
    @Update("UPDATE security_alert SET status = #{status}, last_seen = #{lastSeen}, event_count = #{eventCount}, version = version + 1 WHERE alert_id = #{alertId} AND version = #{version}")
    int update(SecurityAlertPo alert);
    @Select("SELECT alert_id AS alertId, rule_id AS ruleId, risk_level AS riskLevel, fingerprint, subject, status, first_seen AS firstSeen, last_seen AS lastSeen, event_count AS eventCount, version FROM security_alert WHERE alert_id = #{alertId}")
    SecurityAlertPo find(@Param("alertId") String alertId);
    @Select("SELECT alert_id AS alertId, rule_id AS ruleId, risk_level AS riskLevel, fingerprint, subject, status, first_seen AS firstSeen, last_seen AS lastSeen, event_count AS eventCount, version FROM security_alert WHERE fingerprint = #{fingerprint} AND status <> 'CLOSED' AND status <> 'FALSE_POSITIVE'")
    SecurityAlertPo findOpen(@Param("fingerprint") String fingerprint);
}

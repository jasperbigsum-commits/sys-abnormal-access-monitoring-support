package io.github.jasper.monitoring.mybatis.mapper;

import io.github.jasper.monitoring.mybatis.po.AlertDispositionPo;
import io.github.jasper.monitoring.mybatis.po.SecurityAlertPo;
import java.util.List;
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
    @Select("SELECT COUNT(*) FROM alert_event_link WHERE alert_id = #{alertId} AND event_id = #{eventId}")
    int countEventLink(@Param("alertId") String alertId, @Param("eventId") String eventId);
    @Insert("INSERT INTO alert_event_link (alert_id, event_id) VALUES (#{alertId}, #{eventId})")
    int insertEventLink(@Param("alertId") String alertId, @Param("eventId") String eventId);
    @Insert("INSERT INTO alert_disposition (disposition_id, alert_id, disposition_type, operator_id, comment_text, evidence_summary, created_at) VALUES (#{dispositionId}, #{alertId}, #{dispositionType}, #{operatorId}, #{commentText}, #{evidenceSummary}, #{createdAt})")
    int insertDisposition(AlertDispositionPo disposition);
    @Select("SELECT disposition_id AS dispositionId, alert_id AS alertId, disposition_type AS dispositionType, operator_id AS operatorId, comment_text AS commentText, evidence_summary AS evidenceSummary, created_at AS createdAt FROM alert_disposition WHERE alert_id = #{alertId} ORDER BY created_at, disposition_id")
    List<AlertDispositionPo> findDispositions(@Param("alertId") String alertId);
}

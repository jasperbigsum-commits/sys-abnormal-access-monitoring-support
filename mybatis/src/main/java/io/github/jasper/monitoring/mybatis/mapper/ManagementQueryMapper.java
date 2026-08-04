package io.github.jasper.monitoring.mybatis.mapper;

import io.github.jasper.monitoring.mybatis.po.ManagementRowPo;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Insert;
import io.github.jasper.monitoring.mybatis.po.ControlActionPo;
import io.github.jasper.monitoring.mybatis.po.AlertAssignmentPo;
import io.github.jasper.monitoring.mybatis.po.RuleChangePo;

/** Fixed, scope-constrained management queries and optimistic transitions. */
public interface ManagementQueryMapper {
    @Select({"<script>SELECT event_id AS id, result AS status, 1 AS version FROM monitoring_security_event",
        "WHERE system_id=#{scope} AND occurred_at BETWEEN #{from} AND #{to}",
        "ORDER BY <choose><when test=\"sort == 'ACTION'\">action</when><when test=\"sort == 'ID'\">event_id</when><otherwise>occurred_at</otherwise></choose>",
        "<choose><when test=\"descending\">DESC</when><otherwise>ASC</otherwise></choose>, event_id ASC LIMIT #{limit} OFFSET #{offset}</script>"})
    List<ManagementRowPo> events(@Param("scope") String scope, @Param("from") Instant from, @Param("to") Instant to,
                                 @Param("sort") String sort, @Param("descending") boolean descending,
                                 @Param("limit") int limit, @Param("offset") long offset);
    @Select("SELECT COUNT(*) FROM monitoring_security_event WHERE system_id=#{scope} AND occurred_at BETWEEN #{from} AND #{to}")
    long countEvents(@Param("scope") String scope, @Param("from") Instant from, @Param("to") Instant to);
    @Select("SELECT event_id AS id, result AS status, 1 AS version FROM monitoring_security_event WHERE system_id=#{scope} AND event_id=#{id}")
    ManagementRowPo event(@Param("scope") String scope, @Param("id") String id);

    @Select("SELECT a.alert_id AS id, a.status, a.version,(SELECT d.assignee_id FROM monitoring_alert_disposition d WHERE d.alert_id=a.alert_id AND d.assignee_id IS NOT NULL ORDER BY d.created_at DESC,d.disposition_id DESC LIMIT 1) AS assigneeId FROM monitoring_security_alert a WHERE EXISTS (SELECT 1 FROM monitoring_alert_event_link l JOIN monitoring_security_event e ON e.event_id=l.event_id WHERE l.alert_id=a.alert_id AND e.system_id=#{scope}) ORDER BY a.last_seen DESC, a.alert_id ASC LIMIT #{limit} OFFSET #{offset}")
    List<ManagementRowPo> alerts(@Param("scope") String scope, @Param("limit") int limit, @Param("offset") long offset);
    @Select("SELECT COUNT(*) FROM monitoring_security_alert a WHERE EXISTS (SELECT 1 FROM monitoring_alert_event_link l JOIN monitoring_security_event e ON e.event_id=l.event_id WHERE l.alert_id=a.alert_id AND e.system_id=#{scope})")
    long countAlerts(@Param("scope") String scope);
    @Select("SELECT a.alert_id AS id, a.status, a.version,(SELECT d.assignee_id FROM monitoring_alert_disposition d WHERE d.alert_id=a.alert_id AND d.assignee_id IS NOT NULL ORDER BY d.created_at DESC,d.disposition_id DESC LIMIT 1) AS assigneeId FROM monitoring_security_alert a WHERE a.alert_id=#{id} AND EXISTS (SELECT 1 FROM monitoring_alert_event_link l JOIN monitoring_security_event e ON e.event_id=l.event_id WHERE l.alert_id=a.alert_id AND e.system_id=#{scope})")
    ManagementRowPo alert(@Param("scope") String scope, @Param("id") String id);
    @Select({"<script>SELECT d.disposition_id AS id,d.alert_id AS alertId,d.operator_id AS operatorId,d.assignee_id AS assigneeId,d.comment_text AS reason,d.expected_version AS expectedVersion,d.created_at AS createdAt FROM monitoring_alert_disposition d",
        "WHERE d.alert_id=#{id} AND d.assignee_id IS NOT NULL AND EXISTS (SELECT 1 FROM monitoring_alert_event_link l JOIN monitoring_security_event e ON e.event_id=l.event_id WHERE l.alert_id=d.alert_id AND e.system_id=#{scope})",
        "ORDER BY <choose><when test=\"sort == 'ID'\">d.disposition_id</when><otherwise>d.created_at</otherwise></choose>",
        "<choose><when test=\"descending\">DESC</when><otherwise>ASC</otherwise></choose>,d.disposition_id ASC LIMIT #{limit} OFFSET #{offset}</script>"})
    List<AlertAssignmentPo> alertAssignments(@Param("scope") String scope, @Param("id") String id,
        @Param("sort") String sort, @Param("descending") boolean descending, @Param("limit") int limit,
        @Param("offset") long offset);
    @Select("SELECT COUNT(*) FROM monitoring_alert_disposition d WHERE d.alert_id=#{id} AND d.assignee_id IS NOT NULL AND EXISTS (SELECT 1 FROM monitoring_alert_event_link l JOIN monitoring_security_event e ON e.event_id=l.event_id WHERE l.alert_id=d.alert_id AND e.system_id=#{scope})")
    long countAlertAssignments(@Param("scope") String scope, @Param("id") String id);
    @Update("UPDATE monitoring_security_alert SET status=#{status}, version=version+1 WHERE alert_id=#{id} AND version=#{version} AND EXISTS (SELECT 1 FROM monitoring_alert_event_link l JOIN monitoring_security_event e ON e.event_id=l.event_id WHERE l.alert_id=monitoring_security_alert.alert_id AND e.system_id=#{scope})")
    int transitionAlert(@Param("scope") String scope, @Param("id") String id, @Param("version") long version,
                        @Param("status") String status);
    @Insert("INSERT INTO monitoring_alert_disposition(disposition_id,alert_id,disposition_type,operator_id,comment_text,evidence_summary,created_at) VALUES(#{dispositionId},#{alertId},#{type},#{actorId},#{reason},NULL,CURRENT_TIMESTAMP)")
    int insertAlertDisposition(@Param("dispositionId") String dispositionId,@Param("alertId") String alertId,
        @Param("type") String type,@Param("actorId") String actorId,@Param("reason") String reason);
    @Update("UPDATE monitoring_security_alert SET status='IN_PROGRESS', version=version+1 WHERE alert_id=#{id} AND version=#{version} AND status IN ('NEW','ACKNOWLEDGED') AND EXISTS (SELECT 1 FROM monitoring_alert_event_link l JOIN monitoring_security_event e ON e.event_id=l.event_id WHERE l.alert_id=monitoring_security_alert.alert_id AND e.system_id=#{scope})")
    int assignAlert(@Param("scope") String scope, @Param("id") String id, @Param("version") long version);
    @Insert("INSERT INTO monitoring_alert_disposition(disposition_id,alert_id,disposition_type,operator_id,assignee_id,expected_version,comment_text,evidence_summary,created_at) VALUES(#{dispositionId},#{alertId},'IN_PROGRESS',#{actorId},#{assigneeId},#{version},#{reason},NULL,CURRENT_TIMESTAMP)")
    int insertAlertAssignment(@Param("dispositionId") String dispositionId, @Param("alertId") String alertId,
        @Param("version") long version, @Param("actorId") String actorId, @Param("assigneeId") String assigneeId,
        @Param("reason") String reason);
    @Select("SELECT d.disposition_id AS id,d.alert_id AS alertId,d.operator_id AS operatorId,d.assignee_id AS assigneeId,d.comment_text AS reason,d.expected_version AS expectedVersion,d.created_at AS createdAt FROM monitoring_alert_disposition d WHERE d.disposition_id=#{dispositionId} AND d.alert_id=#{id} AND EXISTS (SELECT 1 FROM monitoring_alert_event_link l JOIN monitoring_security_event e ON e.event_id=l.event_id WHERE l.alert_id=d.alert_id AND e.system_id=#{scope})")
    AlertAssignmentPo alertAssignment(@Param("scope") String scope, @Param("id") String id,
        @Param("dispositionId") String dispositionId);

    @Select("SELECT r.rule_id AS id, r.rule_mode AS status, r.rule_version AS version, r.rule_threshold AS threshold FROM monitoring_security_rule r WHERE r.system_id=#{scope} AND r.rule_version=(SELECT MAX(v.rule_version) FROM monitoring_security_rule v WHERE v.system_id=r.system_id AND v.rule_id=r.rule_id) ORDER BY r.rule_id LIMIT #{limit} OFFSET #{offset}")
    List<ManagementRowPo> rules(@Param("scope") String scope, @Param("limit") int limit, @Param("offset") long offset);
    @Select("SELECT COUNT(DISTINCT rule_id) FROM monitoring_security_rule WHERE system_id=#{scope}") long countRules(@Param("scope") String scope);
    @Select("SELECT rule_id AS id, rule_mode AS status, rule_version AS version, rule_threshold AS threshold FROM monitoring_security_rule WHERE system_id=#{scope} AND rule_id=#{id} ORDER BY rule_version DESC LIMIT 1")
    ManagementRowPo rule(@Param("scope") String scope, @Param("id") String id);
    @Insert("INSERT INTO monitoring_security_rule(system_id,rule_id,rule_version,rule_name,rule_definition,risk_level,rule_mode,rule_threshold,enabled,created_at,created_by,change_reason,approved_by,idempotency_key) SELECT r.system_id,r.rule_id,r.rule_version+1,r.rule_name,r.rule_definition,r.risk_level,#{mode},#{threshold},r.enabled,CURRENT_TIMESTAMP,#{actorId},#{reason},#{approverId},#{idempotencyKey} FROM monitoring_security_rule r WHERE r.system_id=#{scope} AND r.rule_id=#{id} AND r.rule_version=#{version} AND NOT EXISTS (SELECT 1 FROM monitoring_security_rule newer WHERE newer.system_id=r.system_id AND newer.rule_id=r.rule_id AND newer.rule_version>r.rule_version)")
    int changeRule(@Param("scope") String scope, @Param("id") String id, @Param("version") long version, @Param("mode") String mode,
        @Param("threshold") long threshold, @Param("actorId") String actorId,
        @Param("approverId") String approverId, @Param("reason") String reason,
        @Param("idempotencyKey") String idempotencyKey);
    @Select("SELECT rule_id AS id,rule_mode AS mode,rule_version AS version,rule_threshold AS threshold,created_by AS actorId,approved_by AS approverId,change_reason AS reason,idempotency_key AS idempotencyKey FROM monitoring_security_rule WHERE system_id=#{scope} AND rule_id=#{id} AND idempotency_key=#{idempotencyKey}")
    RuleChangePo ruleChange(@Param("scope") String scope, @Param("id") String id,
        @Param("idempotencyKey") String idempotencyKey);

    @Select("SELECT whitelist_id AS id, CASE WHEN status='ACTIVE' AND expires_at<=CURRENT_TIMESTAMP THEN 'EXPIRED' ELSE status END AS status, version, subject, rule_id AS ruleId, expires_at AS expiresAt, approved_by AS approvedBy, reason FROM monitoring_security_whitelist WHERE system_id=#{scope} ORDER BY created_at DESC, whitelist_id ASC LIMIT #{limit} OFFSET #{offset}")
    List<ManagementRowPo> whitelists(@Param("scope") String scope, @Param("limit") int limit, @Param("offset") long offset);
    @Select("SELECT COUNT(*) FROM monitoring_security_whitelist WHERE system_id=#{scope}") long countWhitelists(@Param("scope") String scope);
    @Select("SELECT whitelist_id AS id, CASE WHEN status='ACTIVE' AND expires_at<=CURRENT_TIMESTAMP THEN 'EXPIRED' ELSE status END AS status, version, subject, rule_id AS ruleId, expires_at AS expiresAt, approved_by AS approvedBy, reason FROM monitoring_security_whitelist WHERE system_id=#{scope} AND whitelist_id=#{id}")
    ManagementRowPo whitelist(@Param("scope") String scope, @Param("id") String id);
    @Update("UPDATE monitoring_security_whitelist SET status=#{status}, approved_by=#{actorId}, reason=#{reason}, version=version+1 WHERE system_id=#{scope} AND whitelist_id=#{id} AND version=#{version}")
    int transitionWhitelist(@Param("scope") String scope, @Param("id") String id, @Param("version") long version,
                            @Param("status") String status, @Param("actorId") String actorId,
                            @Param("reason") String reason);

    @Select("SELECT c.control_id AS id, c.status, c.version FROM monitoring_control_action c WHERE c.system_id=#{scope} AND c.executed_at BETWEEN #{from} AND #{to} ORDER BY c.executed_at DESC, c.control_id ASC LIMIT #{limit} OFFSET #{offset}")
    List<ManagementRowPo> controls(@Param("scope") String scope, @Param("from") Instant from, @Param("to") Instant to,
                                   @Param("limit") int limit, @Param("offset") long offset);
    @Select("SELECT COUNT(*) FROM monitoring_control_action c WHERE c.system_id=#{scope} AND c.executed_at BETWEEN #{from} AND #{to}")
    long countControls(@Param("scope") String scope, @Param("from") Instant from, @Param("to") Instant to);
    @Select("SELECT c.control_id AS id, c.status, c.version FROM monitoring_control_action c WHERE c.system_id=#{scope} AND c.control_id=#{id}")
    ManagementRowPo control(@Param("scope") String scope, @Param("id") String id);
    @Select("SELECT c.control_id AS controlId,c.system_id AS systemId,c.idempotency_key AS idempotencyKey,c.alert_id AS alertId,c.rule_id AS ruleId,c.subject,c.action_type AS action,c.expires_at AS expiresAt,c.status,c.failure_reason AS failureReason,c.executed_at AS executedAt,c.version FROM monitoring_control_action c WHERE c.system_id=#{scope} AND c.control_id=#{id}")
    ControlActionPo controlCommand(@Param("scope") String scope,@Param("id") String id);
    @Update("UPDATE monitoring_control_action SET status=#{target}, failure_reason=#{reason}, version=version+1 WHERE system_id=#{scope} AND control_id=#{id} AND version=#{version} AND status=#{expected}")
    int transitionControl(@Param("scope") String scope, @Param("id") String id, @Param("version") long version,
                          @Param("expected") String expected, @Param("target") String target,
                          @Param("reason") String reason);
}

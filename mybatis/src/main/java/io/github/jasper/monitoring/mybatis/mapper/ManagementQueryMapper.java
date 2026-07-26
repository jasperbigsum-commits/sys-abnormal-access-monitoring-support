package io.github.jasper.monitoring.mybatis.mapper;

import io.github.jasper.monitoring.mybatis.po.ManagementRowPo;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** Fixed, scope-constrained management queries and optimistic transitions. */
public interface ManagementQueryMapper {
    @Select({"<script>SELECT event_id AS id, result AS status, 1 AS version FROM security_event",
        "WHERE system_id=#{scope} AND occurred_at BETWEEN #{from} AND #{to}",
        "ORDER BY <choose><when test=\"sort == 'ACTION'\">action</when><when test=\"sort == 'ID'\">event_id</when><otherwise>occurred_at</otherwise></choose>",
        "<choose><when test=\"descending\">DESC</when><otherwise>ASC</otherwise></choose>, event_id ASC LIMIT #{limit} OFFSET #{offset}</script>"})
    List<ManagementRowPo> events(@Param("scope") String scope, @Param("from") Instant from, @Param("to") Instant to,
                                 @Param("sort") String sort, @Param("descending") boolean descending,
                                 @Param("limit") int limit, @Param("offset") long offset);
    @Select("SELECT COUNT(*) FROM security_event WHERE system_id=#{scope} AND occurred_at BETWEEN #{from} AND #{to}")
    long countEvents(@Param("scope") String scope, @Param("from") Instant from, @Param("to") Instant to);
    @Select("SELECT event_id AS id, result AS status, 1 AS version FROM security_event WHERE system_id=#{scope} AND event_id=#{id}")
    ManagementRowPo event(@Param("scope") String scope, @Param("id") String id);

    @Select("SELECT a.alert_id AS id, a.status, a.version FROM security_alert a WHERE EXISTS (SELECT 1 FROM alert_event_link l JOIN security_event e ON e.event_id=l.event_id WHERE l.alert_id=a.alert_id AND e.system_id=#{scope}) ORDER BY a.last_seen DESC, a.alert_id ASC LIMIT #{limit} OFFSET #{offset}")
    List<ManagementRowPo> alerts(@Param("scope") String scope, @Param("limit") int limit, @Param("offset") long offset);
    @Select("SELECT COUNT(*) FROM security_alert a WHERE EXISTS (SELECT 1 FROM alert_event_link l JOIN security_event e ON e.event_id=l.event_id WHERE l.alert_id=a.alert_id AND e.system_id=#{scope})")
    long countAlerts(@Param("scope") String scope);
    @Select("SELECT a.alert_id AS id, a.status, a.version FROM security_alert a WHERE a.alert_id=#{id} AND EXISTS (SELECT 1 FROM alert_event_link l JOIN security_event e ON e.event_id=l.event_id WHERE l.alert_id=a.alert_id AND e.system_id=#{scope})")
    ManagementRowPo alert(@Param("scope") String scope, @Param("id") String id);
    @Update("UPDATE security_alert SET status=#{status}, version=version+1 WHERE alert_id=#{id} AND version=#{version} AND EXISTS (SELECT 1 FROM alert_event_link l JOIN security_event e ON e.event_id=l.event_id WHERE l.alert_id=security_alert.alert_id AND e.system_id=#{scope})")
    int transitionAlert(@Param("scope") String scope, @Param("id") String id, @Param("version") long version,
                        @Param("status") String status);

    @Select("SELECT rule_id AS id, rule_mode AS status, rule_version AS version FROM security_rule ORDER BY rule_id, rule_version DESC LIMIT #{limit} OFFSET #{offset}")
    List<ManagementRowPo> rules(@Param("limit") int limit, @Param("offset") long offset);
    @Select("SELECT COUNT(DISTINCT rule_id) FROM security_rule") long countRules();
    @Select("SELECT rule_id AS id, rule_mode AS status, rule_version AS version FROM security_rule WHERE rule_id=#{id} ORDER BY rule_version DESC LIMIT 1")
    ManagementRowPo rule(@Param("id") String id);

    @Select("SELECT whitelist_id AS id, status, version FROM security_whitelist WHERE system_id=#{scope} ORDER BY created_at DESC, whitelist_id ASC LIMIT #{limit} OFFSET #{offset}")
    List<ManagementRowPo> whitelists(@Param("scope") String scope, @Param("limit") int limit, @Param("offset") long offset);
    @Select("SELECT COUNT(*) FROM security_whitelist WHERE system_id=#{scope}") long countWhitelists(@Param("scope") String scope);
    @Select("SELECT whitelist_id AS id, status, version FROM security_whitelist WHERE system_id=#{scope} AND whitelist_id=#{id}")
    ManagementRowPo whitelist(@Param("scope") String scope, @Param("id") String id);
    @Update("UPDATE security_whitelist SET status=#{status}, approved_by=#{actorId}, reason=#{reason}, version=version+1 WHERE system_id=#{scope} AND whitelist_id=#{id} AND version=#{version}")
    int transitionWhitelist(@Param("scope") String scope, @Param("id") String id, @Param("version") long version,
                            @Param("status") String status, @Param("actorId") String actorId,
                            @Param("reason") String reason);

    @Select("SELECT c.control_id AS id, c.status, c.version FROM control_action c WHERE c.executed_at BETWEEN #{from} AND #{to} AND EXISTS (SELECT 1 FROM alert_event_link l JOIN security_event e ON e.event_id=l.event_id WHERE l.alert_id=c.alert_id AND e.system_id=#{scope}) ORDER BY c.executed_at DESC, c.control_id ASC LIMIT #{limit} OFFSET #{offset}")
    List<ManagementRowPo> controls(@Param("scope") String scope, @Param("from") Instant from, @Param("to") Instant to,
                                   @Param("limit") int limit, @Param("offset") long offset);
    @Select("SELECT COUNT(*) FROM control_action c WHERE c.executed_at BETWEEN #{from} AND #{to} AND EXISTS (SELECT 1 FROM alert_event_link l JOIN security_event e ON e.event_id=l.event_id WHERE l.alert_id=c.alert_id AND e.system_id=#{scope})")
    long countControls(@Param("scope") String scope, @Param("from") Instant from, @Param("to") Instant to);
    @Select("SELECT c.control_id AS id, c.status, c.version FROM control_action c WHERE c.control_id=#{id} AND EXISTS (SELECT 1 FROM alert_event_link l JOIN security_event e ON e.event_id=l.event_id WHERE l.alert_id=c.alert_id AND e.system_id=#{scope})")
    ManagementRowPo control(@Param("scope") String scope, @Param("id") String id);
    @Update("UPDATE control_action SET status=#{target}, failure_reason=#{reason}, version=version+1 WHERE control_id=#{id} AND version=#{version} AND status=#{expected} AND EXISTS (SELECT 1 FROM alert_event_link l JOIN security_event e ON e.event_id=l.event_id WHERE l.alert_id=control_action.alert_id AND e.system_id=#{scope})")
    int transitionControl(@Param("scope") String scope, @Param("id") String id, @Param("version") long version,
                          @Param("expected") String expected, @Param("target") String target,
                          @Param("reason") String reason);
}

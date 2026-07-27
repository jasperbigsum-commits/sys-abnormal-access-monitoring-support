package io.github.jasper.monitoring.audit.spring2.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.math.BigDecimal;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** MyBatis boundary for all state owned by the Boot 2 reference host. */
public interface AuditFixtureMapper {
    @Insert("INSERT INTO audit_account(user_id,organization_id,status) VALUES(#{userId},#{organizationId},#{status})")
    int insertAccount(@Param("userId") String userId, @Param("organizationId") String organizationId,
                      @Param("status") String status);

    @Select("SELECT user_id userId, organization_id organizationId, status, failed_login_count failedLoginCount, "
        + "challenge_until challengeUntil, query_block_until queryBlockUntil FROM audit_account WHERE user_id=#{userId}")
    Map<String, Object> findAccount(@Param("userId") String userId);

    @Update("UPDATE audit_account SET failed_login_count=failed_login_count+1 WHERE user_id=#{userId}")
    int incrementFailedLogins(@Param("userId") String userId);

    @Insert("INSERT INTO audit_control_state(idempotency_key,subject,control_type,expires_at,execution_count) "
        + "VALUES(#{key},#{subject},#{type},#{expiresAt},1)")
    int insertControl(@Param("key") String key, @Param("subject") String subject,
                      @Param("type") String type, @Param("expiresAt") Instant expiresAt);

    @Select("SELECT COUNT(*) FROM audit_control_state WHERE subject=#{subject} AND control_type=#{type} "
        + "AND (expires_at IS NULL OR expires_at>#{now})")
    long countActiveControl(@Param("subject") String subject, @Param("type") String type,
                            @Param("now") Instant now);

    @Select("SELECT execution_count FROM audit_control_state WHERE idempotency_key=#{key}")
    Integer controlExecutionCount(@Param("key") String key);

    @Insert("INSERT INTO audit_report(report_id,organization_id,sensitivity) VALUES(#{reportId},#{organizationId},#{sensitivity})")
    int insertReport(@Param("reportId") String reportId, @Param("organizationId") String organizationId,
                     @Param("sensitivity") String sensitivity);

    @Select("SELECT report_id reportId, organization_id organizationId, sensitivity FROM audit_report WHERE report_id=#{reportId}")
    Map<String, Object> findReport(@Param("reportId") String reportId);

    @Insert("INSERT INTO audit_report_row(report_id,row_id,organization_id,display_value,amount,sensitive_value) "
        + "VALUES(#{reportId},#{rowId},#{organizationId},#{displayValue},#{amount},#{sensitiveValue})")
    int insertReportRow(@Param("reportId") String reportId,@Param("rowId") long rowId,
        @Param("organizationId") String organizationId,@Param("displayValue") String displayValue,
        @Param("amount") BigDecimal amount,@Param("sensitiveValue") String sensitiveValue);

    @Select({"<script>SELECT COUNT(*) FROM audit_report_row WHERE report_id=#{reportId}",
        "<if test='minId != null'> AND row_id &gt;= #{minId}</if>",
        "<if test='maxId != null'> AND row_id &lt;= #{maxId}</if>",
        "<if test='selectedIds != null and !selectedIds.isEmpty()'> AND row_id IN",
        "<foreach collection='selectedIds' item='id' open='(' separator=',' close=')'>#{id}</foreach></if>",
        "</script>"})
    long countReportRows(@Param("reportId") String reportId,@Param("minId") Long minId,
        @Param("maxId") Long maxId,@Param("selectedIds") List<Long> selectedIds);

    @Select({"<script>SELECT row_id rowId,display_value displayValue,amount,sensitive_value sensitiveValue",
        "FROM audit_report_row WHERE report_id=#{reportId}",
        "<if test='minId != null'> AND row_id &gt;= #{minId}</if>",
        "<if test='maxId != null'> AND row_id &lt;= #{maxId}</if>",
        "<if test='selectedIds != null and !selectedIds.isEmpty()'> AND row_id IN",
        "<foreach collection='selectedIds' item='id' open='(' separator=',' close=')'>#{id}</foreach></if>",
        "ORDER BY row_id</script>"})
    List<Map<String,Object>> findReportRows(@Param("reportId") String reportId,@Param("minId") Long minId,
        @Param("maxId") Long maxId,@Param("selectedIds") List<Long> selectedIds);

    @Select("SELECT COALESCE(SUM(row_count),0) FROM audit_export_ledger WHERE user_id=#{userId} "
        + "AND outcome='SUCCEEDED' AND occurred_at>=#{start} AND occurred_at<#{end}")
    long sumExports(@Param("userId") String userId,@Param("start") Instant start,@Param("end") Instant end);

    @Insert("INSERT INTO audit_export_ledger(export_id,user_id,report_id,row_count,outcome,occurred_at) "
        + "VALUES(#{exportId},#{userId},#{reportId},#{rowCount},#{outcome},#{at})")
    int insertExport(@Param("exportId") String exportId,@Param("userId") String userId,
        @Param("reportId") String reportId,@Param("rowCount") long rowCount,@Param("outcome") String outcome,
        @Param("at") Instant at);

    @Insert("INSERT INTO audit_user_role(user_id,role_id,granted_by,granted_at) VALUES(#{userId},#{roleId},#{actorId},#{at})")
    int insertRole(@Param("userId") String userId, @Param("roleId") String roleId,
                   @Param("actorId") String actorId, @Param("at") Instant at);

    @Select("SELECT role_id FROM audit_user_role WHERE user_id=#{userId} ORDER BY role_id")
    List<String> findRoles(@Param("userId") String userId);

    @Insert("INSERT INTO audit_session(session_id,user_id,status,created_at) VALUES(#{sessionId},#{userId},'ACTIVE',#{at})")
    int insertSession(@Param("sessionId") String sessionId, @Param("userId") String userId,
                      @Param("at") Instant at);

    @Update("UPDATE audit_session SET status='REVOKED', revoked_at=#{at} WHERE user_id=#{userId} AND status='ACTIVE'")
    int revokeSessions(@Param("userId") String userId, @Param("at") Instant at);

    @Select("SELECT COUNT(*) FROM audit_session WHERE user_id=#{userId} AND status='ACTIVE'")
    long countActiveSessions(@Param("userId") String userId);

    @Select("SELECT COUNT(*) FROM audit_session WHERE session_id=#{sessionId} AND status='ACTIVE'")
    long isActiveSession(@Param("sessionId") String sessionId);

    @Select("SELECT COUNT(*) FROM audit_control_state") long countControls();
    @Select("SELECT COUNT(*) FROM audit_export_ledger") long countExports();
    @Select("SELECT COUNT(*) FROM audit_notification_attempt") long countNotificationAttempts();
}

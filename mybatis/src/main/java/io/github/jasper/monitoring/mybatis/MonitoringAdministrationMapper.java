package io.github.jasper.monitoring.mybatis;

import io.github.jasper.monitoring.api.RiskLevel;
import io.github.jasper.monitoring.api.RuleMode;
import io.github.jasper.monitoring.api.DispositionType;
import java.time.Instant;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;

/**
 * Optional management-side statements for versioned rules and append-only alert history.
 * Applications obtain this mapper from an opened {@code SqlSession} after registration.
 */
public interface MonitoringAdministrationMapper {

    /**
     * Inserts one immutable version of a rule definition.
     * Rule evolution must create a new version instead of updating an existing definition.
     *
     * @return number of inserted rows
     */
    @Insert({
        "INSERT INTO security_rule (rule_id, rule_version, rule_name, rule_definition, risk_level, rule_mode, enabled,",
        "created_at, created_by)",
        "VALUES (#{ruleId}, #{ruleVersion}, #{ruleName}, #{ruleDefinition}, #{riskLevel}, #{ruleMode}, #{enabled},",
        "#{createdAt}, #{createdBy})"
    })
    int insertRule(@Param("ruleId") String ruleId, @Param("ruleVersion") int ruleVersion,
                   @Param("ruleName") String ruleName, @Param("ruleDefinition") String ruleDefinition,
                   @Param("riskLevel") RiskLevel riskLevel, @Param("ruleMode") RuleMode ruleMode,
                   @Param("enabled") boolean enabled, @Param("createdAt") Instant createdAt,
                   @Param("createdBy") String createdBy);

    /**
     * Appends an operator disposition without overwriting earlier alert history.
     *
     * @return number of inserted rows
     */
    @Insert({
        "INSERT INTO alert_disposition (disposition_id, alert_id, disposition_type, operator_id, comment_text,",
        "evidence_summary, created_at)",
        "VALUES (#{dispositionId}, #{alertId}, #{dispositionType}, #{operatorId}, #{commentText},",
        "#{evidenceSummary}, #{createdAt})"
    })
    int appendAlertDisposition(@Param("dispositionId") String dispositionId, @Param("alertId") String alertId,
                               @Param("dispositionType") DispositionType dispositionType,
                               @Param("operatorId") String operatorId, @Param("commentText") String commentText,
                               @Param("evidenceSummary") String evidenceSummary, @Param("createdAt") Instant createdAt);

    /**
     * Inserts an approved, expiring whitelist entry.
     * Callers must provide a reviewable reason, approver, and expiration time.
     *
     * @return number of inserted rows
     */
    @Insert({
        "INSERT INTO security_whitelist (rule_id, subject, reason, approved_by, expires_at, created_at)",
        "VALUES (#{ruleId}, #{subject}, #{reason}, #{approvedBy}, #{expiresAt}, #{createdAt})"
    })
    int insertWhitelist(@Param("ruleId") String ruleId, @Param("subject") String subject,
                        @Param("reason") String reason, @Param("approvedBy") String approvedBy,
                        @Param("expiresAt") Instant expiresAt, @Param("createdAt") Instant createdAt);
}

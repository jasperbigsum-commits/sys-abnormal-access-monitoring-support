package io.github.jasper.monitoring.mybatis;

import io.github.jasper.monitoring.mybatis.po.PersistedRuleDefinition;
import io.github.jasper.monitoring.api.RiskLevel;
import io.github.jasper.monitoring.api.RuleMode;
import io.github.jasper.monitoring.api.DispositionType;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 面向管理端的 MyBatis 语句集合。
 *
 * <p>负责规则版本、告警处置和白名单等管理数据；应用在完成注册后从已打开的 {@code SqlSession} 获取此 Mapper。
 * 它不是运行时监测入口，也不会自动修改已冻结的内部代码规则。</p>
 */
public interface MonitoringAdministrationMapper {

    /**
     * 查询全部持久化规则版本，供管理端审计和版本对比。
     *
     * <p>返回项来源固定为 {@code PERSISTED} 且可变。内置代码规则由只读 typed rule catalog
     * 单独暴露，不在此查询中，以免误认为能够在线改写代码规则。</p>
     *
     * @return 按规则 ID、版本倒序排列的持久化规则版本
     */
    @Select({
        "SELECT rule_id AS ruleId, rule_version AS ruleVersion, rule_name AS ruleName,",
        "rule_definition AS ruleDefinition, risk_level AS riskLevel, rule_mode AS ruleMode, enabled AS enabled,",
        "created_at AS createdAt, created_by AS createdBy",
        "FROM security_rule ORDER BY rule_id ASC, rule_version DESC"
    })
    List<PersistedRuleDefinition> findRuleVersions();

    /**
     * 动态切换已持久化规则版本的管理启停状态。
     *
     * <p>该操作不修改版本定义本体；规则条件、风险等级或动作改变时必须插入新版本，保留审计历史。当前
     * 组件不会自动把本表编译为运行时规则，启停是否生效由宿主批准的动态规则加载器决定。</p>
     *
     * @param ruleId 稳定规则标识
     * @param ruleVersion 要管理的版本号
     * @param enabled 是否在管理侧启用
     * @return 被更新的记录数
     */
    @Update("UPDATE security_rule SET enabled = #{enabled} WHERE rule_id = #{ruleId} AND rule_version = #{ruleVersion}")
    int setRuleEnabled(@Param("ruleId") String ruleId, @Param("ruleVersion") int ruleVersion,
                       @Param("enabled") boolean enabled);

    /**
     * 新增一条不可覆盖的规则定义版本。
     *
     * <p>规则条件演进必须插入新版本，不能更新已有版本的定义内容。</p>
     *
     * @return 成功插入的记录数
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
     * 追加操作人处置记录，不覆盖已有告警处置历史。
     *
     * @return 成功插入的记录数
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
     * 新增经审批且带有效期的白名单记录。
     *
     * <p>调用方必须提供可审核的原因、审批人和到期时间。</p>
     *
     * @return 成功插入的记录数
     */
    @Insert({
        "INSERT INTO security_whitelist (rule_id, subject, reason, approved_by, expires_at, created_at)",
        "VALUES (#{ruleId}, #{subject}, #{reason}, #{approvedBy}, #{expiresAt}, #{createdAt})"
    })
    int insertWhitelist(@Param("ruleId") String ruleId, @Param("subject") String subject,
                        @Param("reason") String reason, @Param("approvedBy") String approvedBy,
                        @Param("expiresAt") Instant expiresAt, @Param("createdAt") Instant createdAt);
}

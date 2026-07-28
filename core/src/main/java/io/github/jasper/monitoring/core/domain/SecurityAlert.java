package io.github.jasper.monitoring.core.domain;

import io.github.jasper.monitoring.core.application.AlertLifecycleService;



import io.github.jasper.monitoring.api.AlertStatus;
import io.github.jasper.monitoring.api.RiskLevel;
import java.time.Instant;

/**
 * 可变告警摘要，其生命周期历史由独立的不可变处置记录保存。
 *
 * <p>调用方应通过 {@link AlertLifecycleService} 迁移状态，而不是直接覆盖审计历史。</p>
 */
public final class SecurityAlert {
    private final String alertId;
    private final String ruleId;
    private final RiskLevel riskLevel;
    private final String fingerprint;
    private final String subject;
    private final AlertStatus status;
    private final Instant firstSeen;
    private final Instant lastSeen;
    private final int eventCount;
    private final long version;

    /**
     * 重建或创建一条告警摘要。
     *
     * @param alertId 告警标识
     * @param ruleId 来源规则标识
     * @param riskLevel 告警风险级别
     * @param fingerprint 用于同一规则范围打开告警去重的稳定键
     * @param subject 告警关联的主体
     * @param status 当前生命周期状态
     * @param firstSeen 首次观测时间
     * @param lastSeen 最近观测时间
     * @param eventCount 本摘要代表的已关联观测数量
     */
    public SecurityAlert(String alertId, String ruleId, RiskLevel riskLevel, String fingerprint, String subject,
                         AlertStatus status, Instant firstSeen, Instant lastSeen, int eventCount) {
        this(alertId, ruleId, riskLevel, fingerprint, subject, status, firstSeen, lastSeen, eventCount, 0L);
    }
    public SecurityAlert(String alertId, String ruleId, RiskLevel riskLevel, String fingerprint, String subject,
                         AlertStatus status, Instant firstSeen, Instant lastSeen, int eventCount, long version) {
        this.alertId = alertId;
        this.ruleId = ruleId;
        this.riskLevel = riskLevel;
        this.fingerprint = fingerprint;
        this.subject = subject;
        this.status = status;
        this.firstSeen = firstSeen;
        this.lastSeen = lastSeen;
        this.eventCount = eventCount;
        this.version = version;
    }
    /**
     * @param alertId 新告警标识
     * @param match 规则命中证据
     * @param at 首次观测时间
     * @return 处于 {@link AlertStatus#NEW} 状态的新告警
     */
    public static SecurityAlert open(String alertId, RuleMatch match, Instant at) {
        return new SecurityAlert(alertId, match.getRuleId(), match.getRiskLevel(), match.fingerprint(), match.getSubject(),
            AlertStatus.NEW, at, at, 1);
    }
    /**
     * @param at 最近观测时间
     * @return 已刷新观测时间且事件数加一的副本
     */
    public SecurityAlert observed(Instant at) {
        return new SecurityAlert(alertId, ruleId, riskLevel, fingerprint, subject, status, firstSeen, at, eventCount + 1);
    }
    /**
     * @param newStatus 由 {@link AlertLifecycleService} 选择的新生命周期状态
     * @return 使用新状态的副本
     */
    public SecurityAlert withStatus(AlertStatus newStatus) {
        return new SecurityAlert(alertId, ruleId, riskLevel, fingerprint, subject, newStatus, firstSeen, lastSeen, eventCount);
    }
    /** @return 告警标识 */
    public String getAlertId() { return alertId; }
    /** @return 触发告警的规则标识 */
    public String getRuleId() { return ruleId; }
    /** @return 当前风险级别 */
    public RiskLevel getRiskLevel() { return riskLevel; }
    /** @return 规则范围去重使用的稳定指纹 */
    public String getFingerprint() { return fingerprint; }
    /** @return 告警关联主体 */
    public String getSubject() { return subject; }
    /** @return 当前生命周期状态 */
    public AlertStatus getStatus() { return status; }
    /** @return 首次观测时间 */
    public Instant getFirstSeen() { return firstSeen; }
    /** @return 最近观测时间 */
    public Instant getLastSeen() { return lastSeen; }
    /** @return 已关联事件数量 */
    public int getEventCount() { return eventCount; }
    /** @return 告警摘要版本号，用于并发更新控制 */
    public long getVersion() { return version; }
}

package io.github.jasper.monitoring.mybatis.po;

import io.github.jasper.monitoring.api.AlertStatus;
import io.github.jasper.monitoring.api.RiskLevel;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/** Persistent representation of one {@code security_alert} row. */
@Getter
@Setter
public final class SecurityAlertPo {
    /** 告警唯一标识。 */
    private String alertId;
    /** 命中告警的规则稳定标识。 */
    private String ruleId;
    /** 告警的风险等级。 */
    private RiskLevel riskLevel;
    /** 用于告警去重的指纹。 */
    private String fingerprint;
    /** 告警关联的主体。 */
    private String subject;
    /** 告警当前生命周期状态。 */
    private AlertStatus status;
    /** 首次发现该告警的时间。 */
    private Instant firstSeen;
    /** 最近一次发现该告警的时间。 */
    private Instant lastSeen;
    /** 已关联的安全事件数量。 */
    private int eventCount;
    private long version;
}

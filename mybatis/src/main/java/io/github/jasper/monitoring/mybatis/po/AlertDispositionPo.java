package io.github.jasper.monitoring.mybatis.po;

import io.github.jasper.monitoring.api.DispositionType;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/** Persistent representation of one {@code alert_disposition} row. */
@Getter
@Setter
public final class AlertDispositionPo {
    /** 处置记录唯一标识。 */
    private String dispositionId;
    /** 被处置的告警唯一标识。 */
    private String alertId;
    /** 告警生命周期处置类型。 */
    private DispositionType dispositionType;
    /** 已认证操作人标识。 */
    private String operatorId;
    /** 操作人填写的处置说明。 */
    private String commentText;
    /** 用于支撑处置结论的证据摘要。 */
    private String evidenceSummary;
    /** 处置记录创建时间。 */
    private Instant createdAt;
}

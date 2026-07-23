package io.github.jasper.monitoring.core.domain;


import io.github.jasper.monitoring.api.DispositionType;
import java.time.Instant;
import java.util.Objects;

/**
 * 附着于告警的不可变、仅追加操作人处置记录。
 *
 * <p>告警进入终态后仍保留该记录，用于还原处置过程与审计责任。</p>
 */
public final class AlertDisposition {
    private final String dispositionId;
    private final String alertId;
    private final DispositionType dispositionType;
    private final String operatorId;
    private final String commentText;
    private final String evidenceSummary;
    private final Instant createdAt;

    /**
     * 创建可审计的生命周期处置记录。
     *
     * @param dispositionId 本不可变记录的唯一标识
     * @param alertId 被更新的告警标识
     * @param dispositionType 操作人执行的生命周期动作
     * @param operatorId 作出决策的已认证操作人
     * @param commentText 操作人原因说明，由生命周期服务校验
     * @param evidenceSummary 终态决策的支撑证据摘要，按需提供
     * @param createdAt 服务端生成的决策时间
     */
    public AlertDisposition(String dispositionId, String alertId, DispositionType dispositionType, String operatorId,
                            String commentText, String evidenceSummary, Instant createdAt) {
        this.dispositionId = requiredText(dispositionId, "dispositionId");
        this.alertId = requiredText(alertId, "alertId");
        this.dispositionType = Objects.requireNonNull(dispositionType, "dispositionType");
        this.operatorId = requiredText(operatorId, "operatorId");
        this.commentText = commentText;
        this.evidenceSummary = evidenceSummary;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    }

    /** @return 本处置记录的唯一标识 */
    public String getDispositionId() { return dispositionId; }
    /** @return 被处置的告警标识 */
    public String getAlertId() { return alertId; }
    /** @return 操作人采取的生命周期动作 */
    public DispositionType getDispositionType() { return dispositionType; }
    /** @return 作出决策的已认证操作人标识 */
    public String getOperatorId() { return operatorId; }
    /** @return 操作人填写的处置原因 */
    public String getCommentText() { return commentText; }
    /** @return 支撑终态决策的证据摘要；非终态时可为 {@code null} */
    public String getEvidenceSummary() { return evidenceSummary; }
    /** @return 服务端记录该决策的时间 */
    public Instant getCreatedAt() { return createdAt; }

    private static String requiredText(String value, String name) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value;
    }
}

package io.github.jasper.monitoring.core.application;


import io.github.jasper.monitoring.core.port.MonitoringRepository;
import io.github.jasper.monitoring.core.domain.SecurityAlert;
import io.github.jasper.monitoring.core.domain.AlertDisposition;
import io.github.jasper.monitoring.api.AlertStatus;
import io.github.jasper.monitoring.api.DispositionType;
import io.github.jasper.monitoring.api.error.MonitoringErrorCode;
import io.github.jasper.monitoring.api.error.MonitoringStateException;
import io.github.jasper.monitoring.api.error.MonitoringValidationException;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * 应用已校验的告警生命周期决策，并保留不可变审计轨迹。
 *
 * <p>仓储实现必须将状态变更及其对应的处置记录一并持久化。该服务不负责认证操作人，
 * 调用方必须先完成宿主权限校验再调用。</p>
 */
public final class AlertLifecycleService {
    private final MonitoringRepository repository;
    private final Clock clock;

    /**
     * 创建告警生命周期服务。
     *
     * @param repository 告警和仅追加处置记录的持久化端口
     * @param clock 服务端处置时间来源
     */
    public AlertLifecycleService(MonitoringRepository repository, Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * 确认一条新告警。
     *
     * @param alertId 待确认的告警标识
     * @param operatorId 已认证操作人标识
     * @param commentText 非空的确认原因
     * @return 状态变为 {@link AlertStatus#ACKNOWLEDGED} 的告警
     * @throws IllegalStateException 告警不是新建状态时
     */
    public SecurityAlert acknowledge(String alertId, String operatorId, String commentText) {
        return transition(alertId, operatorId, commentText, null, DispositionType.ACKNOWLEDGED, AlertStatus.ACKNOWLEDGED);
    }

    /**
     * 将已确认告警标记为调查中。
     *
     * @param alertId 待更新的告警标识
     * @param operatorId 已认证操作人标识
     * @param commentText 非空的转入调查原因
     * @return 状态变为 {@link AlertStatus#IN_PROGRESS} 的告警
     * @throws IllegalStateException 告警尚未确认时
     */
    public SecurityAlert startInvestigation(String alertId, String operatorId, String commentText) {
        return transition(alertId, operatorId, commentText, null, DispositionType.IN_PROGRESS, AlertStatus.IN_PROGRESS);
    }

    /**
     * 关闭已确认或调查中的告警，并记录支撑证据。
     *
     * @param alertId 待关闭的告警标识
     * @param operatorId 已认证操作人标识
     * @param commentText 非空的关闭原因
     * @param evidenceSummary 非空的关闭支撑证据摘要
     * @return 状态变为 {@link AlertStatus#CLOSED} 的告警
     * @throws IllegalStateException 告警未处于已确认或调查中状态时
     */
    public SecurityAlert close(String alertId, String operatorId, String commentText, String evidenceSummary) {
        requireText(evidenceSummary, "evidenceSummary");
        return transition(alertId, operatorId, commentText, evidenceSummary, DispositionType.CLOSED, AlertStatus.CLOSED);
    }

    /**
     * 将打开的告警判定为误报，并保留操作人决策。
     *
     * @param alertId 待判定的告警标识
     * @param operatorId 已认证操作人标识
     * @param commentText 非空的误报判定原因
     * @param evidenceSummary 支撑判定的证据摘要
     * @return 状态变为 {@link AlertStatus#FALSE_POSITIVE} 的告警
     * @throws IllegalStateException 告警已进入终态时
     */
    public SecurityAlert falsePositive(String alertId, String operatorId, String commentText, String evidenceSummary) {
        return transition(alertId, operatorId, commentText, evidenceSummary, DispositionType.FALSE_POSITIVE,
            AlertStatus.FALSE_POSITIVE);
    }

    private SecurityAlert transition(String alertId, String operatorId, String commentText, String evidenceSummary,
                                     DispositionType dispositionType, AlertStatus targetStatus) {
        requireText(alertId, "alertId");
        requireText(operatorId, "operatorId");
        requireText(commentText, "commentText");
        return repository.inTransaction(() -> {
            SecurityAlert alert = repository.findAlert(alertId)
                .orElseThrow(() -> new MonitoringValidationException(MonitoringErrorCode.ALERT_NOT_FOUND,
                    "Alert not found"));
            assertTransitionAllowed(alert.getStatus(), dispositionType);

            Instant now = Instant.now(clock);
            AlertDisposition disposition = new AlertDisposition(UUID.randomUUID().toString(), alertId, dispositionType,
                operatorId, commentText, evidenceSummary, now);
            SecurityAlert updated = alert.withStatus(targetStatus);
            repository.appendAlertDisposition(disposition);
            repository.saveAlert(updated);
            return updated;
        });
    }

    private static void assertTransitionAllowed(AlertStatus currentStatus, DispositionType dispositionType) {
        if (dispositionType == DispositionType.ACKNOWLEDGED) {
            if (currentStatus == AlertStatus.NEW) { return; }
            throw invalidTransition("Only new alerts can be acknowledged");
        }
        if (dispositionType == DispositionType.CLOSED) {
            if (currentStatus == AlertStatus.ACKNOWLEDGED || currentStatus == AlertStatus.IN_PROGRESS) { return; }
            throw invalidTransition("Only acknowledged or in-progress alerts can be closed");
        }
        if (dispositionType == DispositionType.IN_PROGRESS) {
            if (currentStatus == AlertStatus.ACKNOWLEDGED) { return; }
            throw invalidTransition("Only acknowledged alerts can enter investigation");
        }
        if (dispositionType == DispositionType.FALSE_POSITIVE && currentStatus.isOpen()) { return; }
        throw invalidTransition("Terminal alerts cannot be changed");
    }

    private static void requireText(String value, String name) {
        if (value == null || value.trim().isEmpty()) {
            throw new MonitoringValidationException(MonitoringErrorCode.REQUIRED_FIELD_MISSING,
                name + " is required");
        }
    }

    private static MonitoringStateException invalidTransition(String message) {
        return new MonitoringStateException(MonitoringErrorCode.INVALID_ALERT_TRANSITION, message);
    }
}

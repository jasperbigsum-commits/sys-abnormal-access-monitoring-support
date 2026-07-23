package io.github.jasper.monitoring.core.port;

import io.github.jasper.monitoring.core.domain.WhitelistEntry;


import io.github.jasper.monitoring.core.domain.SecurityAlert;
import io.github.jasper.monitoring.core.domain.AlertDisposition;
import io.github.jasper.monitoring.core.domain.SecurityEvent;
import io.github.jasper.monitoring.core.domain.ControlRecord;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 不可变事件、告警状态、审计历史、控制记录和时效白名单的持久化端口。
 *
 * <p>实现必须保留控制动作的幂等语义，且不得删除已追加的告警处置记录。生产实现还应在同一
 * 事务或等效一致性边界内处理告警状态与处置记录，避免审计轨迹断裂。</p>
 */
public interface MonitoringRepository {
    /**
     * Runs monitoring persistence work atomically.
     *
     * <p>Nested calls join the active repository transaction. Implementations must roll back all
     * writes made by {@code work} when it throws a runtime exception.</p>
     *
     * @param work persistence-only callback
     * @param <T> result type
     * @return callback result after the transaction commits
     */
    <T> T inTransaction(TransactionWork<T> work);

    /**
     * 持久化一条已由服务端加盖时间的安全事件。
     *
     * @param event 待写入的不可变事件
     */
    void saveEvent(SecurityEvent event);
    /**
     * @param since 查询下界，包含该时刻
     * @return 发生时间不早于 {@code since} 的事件，按确定性规则评估所需的顺序排列
     */
    List<SecurityEvent> findEventsSince(Instant since);
    /**
     * @param fingerprint 规则与主体范围组成的稳定去重键
     * @return 对应的打开告警；不存在时为空
     */
    Optional<SecurityAlert> findOpenAlert(String fingerprint);
    /**
     * @param alertId 告警标识
     * @return 对应告警，包含终态告警；不存在时为空
     */
    Optional<SecurityAlert> findAlert(String alertId);
    /**
     * 创建或更新告警的可变摘要状态。
     *
     * @param alert 待保存的告警摘要
     */
    void saveAlert(SecurityAlert alert);
    /**
     * 幂等地关联事件与告警。
     *
     * @param alertId 告警标识
     * @param eventId 事件标识
     */
    void linkAlertEvent(String alertId, String eventId);
    /**
     * 追加操作人生命周期处置记录，不得覆盖既有记录。
     *
     * @param disposition 待追加的不可变处置记录
     */
    void appendAlertDisposition(AlertDisposition disposition);
    /**
     * @param alertId 告警标识
     * @return 该告警的全部不可变操作人处置记录，按时间顺序排列
     */
    List<AlertDisposition> findAlertDispositions(String alertId);
    /**
     * @param idempotencyKey 控制动作幂等键
     * @return 已记录的控制结果；首次执行时为空
     */
    Optional<ControlRecord> findControl(String idempotencyKey);
    /**
     * 按幂等键持久化一条控制结果。
     *
     * @param record 待写入的控制审计记录
     */
    void saveControl(ControlRecord record);
    /**
     * @param ruleId 规则标识
     * @param subject 规则关联主体
     * @param at 判断时间
     * @return 未过期白名单是否应在该时刻抑制该规则
     */
    boolean isWhitelisted(String ruleId, String subject, Instant at);
    /**
     * 持久化一条会过期的白名单记录；不支持永久抑制。
     *
     * @param entry 待写入的时效白名单记录
     */
    void addWhitelist(WhitelistEntry entry);
}

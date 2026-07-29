package io.github.jasper.monitoring.core.port;

import io.github.jasper.monitoring.core.domain.AlertDisposition;
import io.github.jasper.monitoring.core.domain.SecurityAlert;
import java.util.List;
import java.util.Optional;

/** 告警摘要与追加式处置记录的持久化边界。 */
public interface AlertRepository {
    /** 保存或更新告警摘要；实现应遵守告警版本并发约束。 */
    void save(SecurityAlert alert);
    /** 按稳定告警标识查询告警。 */
    Optional<SecurityAlert> findAlert(String alertId);
    /** 按告警指纹查询当前仍开放的告警。 */
    Optional<SecurityAlert> findOpen(String fingerprint);
    /** 关联一个已持久化的安全事件，并原子更新告警摘要。 */
    void linkEvent(String alertId, String eventId);
    /** 追加管理处置记录；该记录只能追加，不能更新或删除。 */
    void appendDisposition(AlertDisposition disposition);
    /** 查询告警的全部处置历史，按服务端记录顺序返回。 */
    List<AlertDisposition> findDispositions(String alertId);
}

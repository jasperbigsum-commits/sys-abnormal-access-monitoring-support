package io.github.jasper.monitoring.core.port;

import io.github.jasper.monitoring.core.domain.SecurityEvent;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** 不可变安全事件的持久化边界。 */
public interface EventRepository {
    /**
     * 持久化一条安全事件快照。
     *
     * @param event 已通过输入质量校验的安全事件
     */
    void save(SecurityEvent event);

    /**
     * 按事件标识查询单条事件。
     *
     * @param eventId 事件标识
     * @return 命中时返回事件快照，否则返回空
     */
    Optional<SecurityEvent> findEvent(String eventId);

    /**
     * 查询某系统自指定时间以来的事件序列。
     *
     * @param systemId 来源系统标识
     * @param since 起始时间（含）
     * @return 事件列表
     */
    List<SecurityEvent> findSince(String systemId, Instant since);
}

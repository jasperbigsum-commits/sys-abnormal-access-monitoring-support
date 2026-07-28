package io.github.jasper.monitoring.core.port;

import io.github.jasper.monitoring.core.domain.NotificationDelivery;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** 告警通知投递状态的持久化边界（支持乐观锁并发控制）。 */
public interface NotificationDeliveryRepository {
    /**
     * 按通道与聚合标识查询投递状态。
     *
     * @param channel 投递通道
     * @param aggregateId 关联聚合标识
     * @return 命中时返回投递状态，否则返回空
     */
    Optional<NotificationDelivery> find(String channel, String aggregateId);

    /**
     * 创建初始投递记录。
     *
     * @param delivery 投递状态
     * @return 创建成功返回 {@code true}；主键冲突返回 {@code false}
     */
    boolean create(NotificationDelivery delivery);

    /**
     * 按期望版本执行状态更新。
     *
     * @param delivery 新的投递状态副本
     * @param expectedVersion 期望旧版本
     * @return 版本匹配并更新成功返回 {@code true}
     */
    boolean update(NotificationDelivery delivery, long expectedVersion);

    /**
     * 查询在指定时间前到期且可重试的投递任务。
     *
     * @param channel 投递通道
     * @param at 判断时间
     * @param limit 返回上限
     * @return 到期投递任务列表
     */
    List<NotificationDelivery> findDue(String channel, Instant at, int limit);
}

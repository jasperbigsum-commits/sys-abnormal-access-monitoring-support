package io.github.jasper.monitoring.core.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * 单条外部告警通知投递的不可变版本化状态。
 *
 * <p>每次状态迁移都会返回新副本并递增版本，用于并发更新控制与审计追踪。</p>
 */
public final class NotificationDelivery {
    /** 投递生命周期状态。 */
    public enum Status { PENDING, RETRY_PENDING, DELIVERED, FAILED }

    private final String deliveryId;
    private final String channel;
    private final String aggregateId;
    private final Status status;
    private final int attemptCount;
    private final Instant nextAttemptAt;
    private final String failureCategory;
    private final Instant updatedAt;
    private final long version;

    /**
     * 重建或创建一条投递状态记录。
     *
     * @param deliveryId 投递记录标识
     * @param channel 投递通道标识
     * @param aggregateId 关联聚合标识（如告警标识）
     * @param status 当前投递状态
     * @param attemptCount 已尝试次数
     * @param nextAttemptAt 下次可重试时间
     * @param failureCategory 稳定失败分类
     * @param updatedAt 服务端更新时间
     * @param version 乐观锁版本
     */
    public NotificationDelivery(String deliveryId, String channel, String aggregateId, Status status,
                                int attemptCount, Instant nextAttemptAt, String failureCategory,
                                Instant updatedAt, long version) {
        this.deliveryId = required(deliveryId, "deliveryId");
        this.channel = required(channel, "channel");
        this.aggregateId = required(aggregateId, "aggregateId");
        this.status = Objects.requireNonNull(status, "status");
        if (attemptCount < 0 || version < 0) throw new IllegalArgumentException("negative delivery state");
        this.attemptCount = attemptCount;
        this.nextAttemptAt = nextAttemptAt;
        this.failureCategory = failureCategory;
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
        this.version = version;
        validateState();
    }

    /**
     * 创建初始待投递状态。
     *
     * @param id 投递记录标识
     * @param channel 投递通道标识
     * @param aggregateId 关联聚合标识
     * @param at 创建时间
     * @return 初始投递状态副本
     */
    public static NotificationDelivery pending(String id, String channel, String aggregateId, Instant at) {
        return new NotificationDelivery(id, channel, aggregateId, Status.PENDING, 0, null, null, at, 0);
    }

    /**
     * 认领一次投递尝试并登记租约到期时间。
     *
     * @param at 认领时间
     * @param leaseExpiresAt 本次尝试租约到期时间
     * @return 尝试次数加一后的重试中状态副本
     */
    public NotificationDelivery claim(Instant at, Instant leaseExpiresAt) {
        Objects.requireNonNull(at, "at");
        Objects.requireNonNull(leaseExpiresAt, "leaseExpiresAt");
        if (!leaseExpiresAt.isAfter(at)) throw new IllegalArgumentException("lease must expire after claim time");
        return new NotificationDelivery(deliveryId, channel, aggregateId, Status.RETRY_PENDING, attemptCount + 1,
            leaseExpiresAt, null, at, version + 1);
    }

    /**
     * 标记本条投递已成功送达。
     *
     * @param at 成功时间
     * @return 成功终态副本
     */
    public NotificationDelivery delivered(Instant at) {
        return new NotificationDelivery(deliveryId, channel, aggregateId, Status.DELIVERED, attemptCount,
            null, null, at, version + 1);
    }

    /**
     * 记录一次失败尝试并选择是否进入终态。
     *
     * @param category 稳定失败分类
     * @param nextAt 非终态时的下次重试时间
     * @param terminal 是否终止后续重试
     * @param at 状态更新时间
     * @return 更新后的投递状态副本
     */
    public NotificationDelivery failedAttempt(String category, Instant nextAt, boolean terminal, Instant at) {
        return new NotificationDelivery(deliveryId, channel, aggregateId,
            terminal ? Status.FAILED : Status.RETRY_PENDING, attemptCount, terminal ? null : nextAt,
            required(category, "failureCategory"), at, version + 1);
    }

    /**
     * 判断当前时间是否允许执行投递尝试。
     *
     * @param at 判断时间
     * @return 可立即尝试时返回 {@code true}
     */
    public boolean canAttemptAt(Instant at) {
        return status == Status.PENDING && attemptCount == 0
            || status == Status.RETRY_PENDING && nextAttemptAt != null && !nextAttemptAt.isAfter(at);
    }

    /**
     * 判断是否属于指定通道且到达可尝试时间。
     *
     * @param expectedChannel 期望通道
     * @param at 判断时间
     * @return 同通道且可尝试时返回 {@code true}
     */
    public boolean isDueAt(String expectedChannel, Instant at) {
        return channel.equals(expectedChannel) && canAttemptAt(at);
    }

    /** @return 投递记录标识 */
    public String getDeliveryId() { return deliveryId; }
    /** @return 投递通道标识 */
    public String getChannel() { return channel; }
    /** @return 关联聚合标识 */
    public String getAggregateId() { return aggregateId; }
    /** @return 当前投递状态 */
    public Status getStatus() { return status; }
    /** @return 已尝试次数 */
    public int getAttemptCount() { return attemptCount; }
    /** @return 下次可重试时间；不可重试时为 {@code null} */
    public Instant getNextAttemptAt() { return nextAttemptAt; }
    /** @return 稳定失败分类；未失败时为 {@code null} */
    public String getFailureCategory() { return failureCategory; }
    /** @return 服务端更新时间 */
    public Instant getUpdatedAt() { return updatedAt; }
    /** @return 乐观锁版本 */
    public long getVersion() { return version; }

    /**
     * 校验必填文本字段。
     *
     * @param value 字段值
     * @param field 字段名
     * @return 通过校验的原始字段值
     */
    private static String required(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.trim().isEmpty()) throw new IllegalArgumentException(field + " must not be blank");
        return value;
    }

    private void validateState() {
        if (status == Status.PENDING && (attemptCount != 0 || nextAttemptAt != null || failureCategory != null)) {
            throw new IllegalArgumentException("PENDING delivery must be unattempted");
        }
        if (status == Status.RETRY_PENDING && (attemptCount == 0 || nextAttemptAt == null)) {
            throw new IllegalArgumentException("RETRY_PENDING delivery requires an attempt and retry time");
        }
        if ((status == Status.DELIVERED || status == Status.FAILED) && nextAttemptAt != null) {
            throw new IllegalArgumentException("terminal delivery must not have a retry time");
        }
        if (status == Status.DELIVERED && (attemptCount == 0 || failureCategory != null)) {
            throw new IllegalArgumentException("DELIVERED delivery must contain only successful attempt state");
        }
        if (status == Status.FAILED && (attemptCount == 0 || failureCategory == null)) {
            throw new IllegalArgumentException("FAILED delivery requires a stable failure category");
        }
    }
}

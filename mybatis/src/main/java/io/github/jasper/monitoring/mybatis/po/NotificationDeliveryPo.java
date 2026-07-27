package io.github.jasper.monitoring.mybatis.po;

import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/** Persistent representation of a versioned notification delivery. */
@Getter
@Setter
public final class NotificationDeliveryPo {
    private String deliveryId;
    private String channel;
    private String aggregateId;
    private String status;
    private int attemptCount;
    private Instant nextAttemptAt;
    private String failureCategory;
    private Instant updatedAt;
    private long version;
}

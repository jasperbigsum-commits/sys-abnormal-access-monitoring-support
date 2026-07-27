package io.github.jasper.monitoring.core.port;

import io.github.jasper.monitoring.core.domain.NotificationDelivery;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Persistence boundary for durable, optimistic notification delivery state. */
public interface NotificationDeliveryRepository {
    Optional<NotificationDelivery> find(String channel, String aggregateId);
    boolean create(NotificationDelivery delivery);
    boolean update(NotificationDelivery delivery, long expectedVersion);
    List<NotificationDelivery> findDue(String channel, Instant at, int limit);
}

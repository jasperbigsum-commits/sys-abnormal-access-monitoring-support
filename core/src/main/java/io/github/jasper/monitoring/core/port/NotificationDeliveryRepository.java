package io.github.jasper.monitoring.core.port;

/** Persistence boundary for durable notification delivery state. */
public interface NotificationDeliveryRepository {
    void record(String deliveryId, String channel, String aggregateId, String status);
}

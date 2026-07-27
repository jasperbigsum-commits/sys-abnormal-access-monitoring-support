package io.github.jasper.monitoring.core.domain;

import java.time.Instant;
import java.util.Objects;

/** Immutable, versioned state of one external alert notification delivery. */
public final class NotificationDelivery {
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

    public static NotificationDelivery pending(String id, String channel, String aggregateId, Instant at) {
        return new NotificationDelivery(id, channel, aggregateId, Status.PENDING, 0, null, null, at, 0);
    }

    public NotificationDelivery claim(Instant at, Instant leaseExpiresAt) {
        Objects.requireNonNull(at, "at");
        Objects.requireNonNull(leaseExpiresAt, "leaseExpiresAt");
        if (!leaseExpiresAt.isAfter(at)) throw new IllegalArgumentException("lease must expire after claim time");
        return new NotificationDelivery(deliveryId, channel, aggregateId, Status.RETRY_PENDING, attemptCount + 1,
            leaseExpiresAt, null, at, version + 1);
    }

    public NotificationDelivery delivered(Instant at) {
        return new NotificationDelivery(deliveryId, channel, aggregateId, Status.DELIVERED, attemptCount,
            null, null, at, version + 1);
    }

    public NotificationDelivery failedAttempt(String category, Instant nextAt, boolean terminal, Instant at) {
        return new NotificationDelivery(deliveryId, channel, aggregateId,
            terminal ? Status.FAILED : Status.RETRY_PENDING, attemptCount, terminal ? null : nextAt,
            required(category, "failureCategory"), at, version + 1);
    }

    public boolean canAttemptAt(Instant at) {
        return status == Status.PENDING && attemptCount == 0
            || status == Status.RETRY_PENDING && nextAttemptAt != null && !nextAttemptAt.isAfter(at);
    }

    public boolean isDueAt(String expectedChannel, Instant at) {
        return channel.equals(expectedChannel) && canAttemptAt(at);
    }

    public String getDeliveryId() { return deliveryId; }
    public String getChannel() { return channel; }
    public String getAggregateId() { return aggregateId; }
    public Status getStatus() { return status; }
    public int getAttemptCount() { return attemptCount; }
    public Instant getNextAttemptAt() { return nextAttemptAt; }
    public String getFailureCategory() { return failureCategory; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }

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

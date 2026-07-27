package io.github.jasper.monitoring.core.application.notification;

import io.github.jasper.monitoring.core.domain.NotificationDelivery;
import io.github.jasper.monitoring.core.domain.SecurityAlert;
import io.github.jasper.monitoring.core.port.AlertRepository;
import io.github.jasper.monitoring.core.port.NotificationChannel;
import io.github.jasper.monitoring.core.port.NotificationDeliveryRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Logger;

/** Registers transactional delivery intents and coordinates post-commit, finite at-least-once delivery. */
public final class NotificationDeliveryService {
    private static final Logger LOGGER = Logger.getLogger(NotificationDeliveryService.class.getName());
    private final String channelName;
    private final NotificationChannel channel;
    private final NotificationDeliveryRepository deliveries;
    private final AlertRepository alerts;
    private final Clock clock;
    private final int maxAttempts;
    private final Duration retryDelay;
    private final Duration leaseDuration;

    public NotificationDeliveryService(String channelName, NotificationChannel channel,
        NotificationDeliveryRepository deliveries, AlertRepository alerts, Clock clock,
        int maxAttempts, Duration retryDelay, Duration leaseDuration) {
        this.channelName = required(channelName, "channelName");
        this.channel = Objects.requireNonNull(channel, "channel");
        this.deliveries = Objects.requireNonNull(deliveries, "deliveries");
        this.alerts = Objects.requireNonNull(alerts, "alerts");
        this.clock = Objects.requireNonNull(clock, "clock");
        if (maxAttempts < 1) throw new IllegalArgumentException("maxAttempts must be positive");
        this.maxAttempts = maxAttempts;
        if (retryDelay == null || retryDelay.isNegative() || retryDelay.isZero()) {
            throw new IllegalArgumentException("retryDelay must be positive");
        }
        this.retryDelay = retryDelay;
        if (leaseDuration == null || leaseDuration.isNegative() || leaseDuration.isZero()) {
            throw new IllegalArgumentException("leaseDuration must be positive");
        }
        this.leaseDuration = leaseDuration;
    }

    /** Registers the durable delivery intent in the same transaction as the alert. */
    public void register(SecurityAlert alert) {
        Objects.requireNonNull(alert, "alert");
        Instant now = clock.instant();
        if (!deliveries.find(channelName, alert.getAlertId()).isPresent()) {
            NotificationDelivery pending = NotificationDelivery.pending(deliveryId(channelName, alert.getAlertId()),
                channelName, alert.getAlertId(), now);
            deliveries.create(pending);
        }
    }

    /** Attempts a previously registered delivery after its alert transaction commits. */
    public void deliver(SecurityAlert alert) {
        Objects.requireNonNull(alert, "alert");
        Optional<NotificationDelivery> delivery = deliveries.find(channelName, alert.getAlertId());
        if (delivery.isPresent()) attempt(delivery.get(), alert, clock.instant());
    }

    public void retryDue(int limit) {
        if (limit < 1 || limit > 200) throw new IllegalArgumentException("limit must be between 1 and 200");
        Instant now = clock.instant();
        List<NotificationDelivery> due = deliveries.findDue(channelName, now, limit);
        for (NotificationDelivery delivery : due) {
            try {
                Optional<SecurityAlert> alert = alerts.findAlert(delivery.getAggregateId());
                if (alert.isPresent()) {
                    attempt(delivery, alert.get(), now);
                } else {
                    deliveries.update(delivery.failedAttempt("AGGREGATE_NOT_FOUND", now, true, now),
                        delivery.getVersion());
                }
            } catch (RuntimeException isolatedFailure) {
                // A transient failure for one row must not starve the remainder of this bounded batch.
                LOGGER.warning("Notification retry item failed [category=PERSISTENCE_UNAVAILABLE]");
            }
        }
    }

    private void attempt(NotificationDelivery current, SecurityAlert alert, Instant now) {
        if (!current.canAttemptAt(now)) return;
        if (current.getAttemptCount() >= maxAttempts) {
            String category = current.getFailureCategory() == null
                ? "ATTEMPT_LIMIT_REACHED" : current.getFailureCategory();
            deliveries.update(current.failedAttempt(category, now, true, now), current.getVersion());
            return;
        }
        NotificationDelivery claimed = current.claim(now, now.plus(leaseDuration));
        if (!deliveries.update(claimed, current.getVersion())) return;
        try {
            channel.notify(claimed.getDeliveryId(), alert);
        } catch (RuntimeException failure) {
            boolean terminal = claimed.getAttemptCount() >= maxAttempts;
            deliveries.update(claimed.failedAttempt("CHANNEL_FAILURE", clock.instant().plus(retryDelay), terminal,
                clock.instant()), claimed.getVersion());
            return;
        }
        deliveries.update(claimed.delivered(clock.instant()), claimed.getVersion());
    }

    private static String deliveryId(String channel, String aggregateId) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(
                (channel + "\u0000" + aggregateId).getBytes(StandardCharsets.UTF_8));
            StringBuilder value = new StringBuilder(64);
            for (byte item : digest) {
                value.append(Character.forDigit((item >>> 4) & 0xf, 16));
                value.append(Character.forDigit(item & 0xf, 16));
            }
            return value.toString();
        } catch (NoSuchAlgorithmException unavailable) {
            throw new IllegalStateException("SHA-256 is required by the runtime", unavailable);
        }
    }

    private static String required(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.trim().isEmpty()) throw new IllegalArgumentException(field + " must not be blank");
        return value;
    }
}

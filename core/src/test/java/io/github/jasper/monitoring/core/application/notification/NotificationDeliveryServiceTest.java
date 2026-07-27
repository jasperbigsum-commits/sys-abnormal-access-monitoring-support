package io.github.jasper.monitoring.core.application.notification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import io.github.jasper.monitoring.api.AlertStatus;
import io.github.jasper.monitoring.api.RiskLevel;
import io.github.jasper.monitoring.core.domain.AlertDisposition;
import io.github.jasper.monitoring.core.domain.NotificationDelivery;
import io.github.jasper.monitoring.core.domain.SecurityAlert;
import io.github.jasper.monitoring.core.port.AlertRepository;
import io.github.jasper.monitoring.core.port.NotificationChannel;
import io.github.jasper.monitoring.core.port.NotificationDeliveryRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class NotificationDeliveryServiceTest {
    @Test
    void retriesWithOneStableDeliveryUntilThirdAttemptSucceeds() {
        MutableClock clock = new MutableClock(Instant.parse("2026-07-27T00:00:00Z"));
        InMemoryDeliveries deliveries = new InMemoryDeliveries();
        SecurityAlert alert = alert();
        StubAlerts alerts = new StubAlerts(alert);
        AtomicInteger calls = new AtomicInteger();
        List<String> deliveryIds = new ArrayList<String>();
        NotificationChannel channel = (deliveryId, value) -> {
            deliveryIds.add(deliveryId);
            if (calls.incrementAndGet() < 3) throw new IllegalStateException("sensitive provider detail");
        };
        NotificationDeliveryService service = new NotificationDeliveryService("email", channel, deliveries, alerts,
            clock, 3, Duration.ofMinutes(1), Duration.ofMinutes(5));

        service.register(alert);
        service.deliver(alert);
        assertEquals(NotificationDelivery.Status.RETRY_PENDING, deliveries.only().getStatus());
        String deliveryId = deliveries.only().getDeliveryId();
        clock.advance(Duration.ofMinutes(1));
        service.retryDue(10);
        clock.advance(Duration.ofMinutes(1));
        service.retryDue(10);

        assertEquals(3, calls.get());
        assertEquals(deliveryId, deliveries.only().getDeliveryId());
        assertEquals(Arrays.asList(deliveryId, deliveryId, deliveryId), deliveryIds);
        assertEquals(3, deliveries.only().getAttemptCount());
        assertEquals(NotificationDelivery.Status.DELIVERED, deliveries.only().getStatus());
        assertEquals("CHANNEL_FAILURE", deliveries.lastFailureCategory);
        assertEquals(0, alerts.saveCalls);
    }

    @Test
    void stopsAfterMaximumAttemptsAndNeverMakesAFourthCall() {
        MutableClock clock = new MutableClock(Instant.parse("2026-07-27T00:00:00Z"));
        InMemoryDeliveries deliveries = new InMemoryDeliveries();
        SecurityAlert alert = alert();
        AtomicInteger calls = new AtomicInteger();
        NotificationDeliveryService service = new NotificationDeliveryService("webhook", (deliveryId, value) -> {
            calls.incrementAndGet();
            throw new IllegalStateException("must not be persisted");
        }, deliveries, new StubAlerts(alert), clock, 3, Duration.ofSeconds(1), Duration.ofSeconds(30));

        service.register(alert);
        service.deliver(alert);
        clock.advance(Duration.ofSeconds(1)); service.retryDue(10);
        clock.advance(Duration.ofSeconds(1)); service.retryDue(10);
        clock.advance(Duration.ofSeconds(1)); service.retryDue(10);

        assertEquals(3, calls.get());
        assertEquals(NotificationDelivery.Status.FAILED, deliveries.only().getStatus());
        assertEquals(3, deliveries.only().getAttemptCount());
        assertNotNull(deliveries.only().getUpdatedAt());
    }

    @Test
    void retriesAfterAnExpiredWorkerClaim() {
        MutableClock clock = new MutableClock(Instant.parse("2026-07-27T00:00:00Z"));
        InMemoryDeliveries deliveries = new InMemoryDeliveries();
        SecurityAlert alert = alert();
        NotificationDelivery pending = NotificationDelivery.pending("delivery-recovery", "email",
            alert.getAlertId(), clock.instant());
        deliveries.create(pending);
        deliveries.update(pending.claim(clock.instant(), clock.instant().plusSeconds(30)), pending.getVersion());
        AtomicInteger calls = new AtomicInteger();
        NotificationDeliveryService service = new NotificationDeliveryService("email",
            (deliveryId, value) -> calls.incrementAndGet(), deliveries, new StubAlerts(alert), clock, 3,
            Duration.ofSeconds(30), Duration.ofSeconds(30));

        service.retryDue(10);
        assertEquals(0, calls.get());
        clock.advance(Duration.ofSeconds(30));
        service.retryDue(10);

        assertEquals(1, calls.get());
        assertEquals(2, deliveries.only().getAttemptCount());
        assertEquals(NotificationDelivery.Status.DELIVERED, deliveries.only().getStatus());
    }

    @Test
    void expiresTheLastClaimWithoutMakingAnExtraCall() {
        MutableClock clock = new MutableClock(Instant.parse("2026-07-27T00:00:00Z"));
        InMemoryDeliveries deliveries = new InMemoryDeliveries();
        SecurityAlert alert = alert();
        NotificationDelivery pending = NotificationDelivery.pending("delivery-terminal-recovery", "email",
            alert.getAlertId(), clock.instant());
        deliveries.create(pending);
        deliveries.update(pending.claim(clock.instant(), clock.instant().plusSeconds(30)), pending.getVersion());
        AtomicInteger calls = new AtomicInteger();
        NotificationDeliveryService service = new NotificationDeliveryService("email",
            (deliveryId, value) -> calls.incrementAndGet(), deliveries, new StubAlerts(alert), clock, 1,
            Duration.ofSeconds(30), Duration.ofSeconds(30));

        clock.advance(Duration.ofSeconds(30));
        service.retryDue(10);

        assertEquals(0, calls.get());
        assertEquals(NotificationDelivery.Status.FAILED, deliveries.only().getStatus());
        assertEquals("ATTEMPT_LIMIT_REACHED", deliveries.only().getFailureCategory());
    }

    @Test
    void marksAnOrphanedDeliveryFailedInsteadOfStarvingTheQueue() {
        MutableClock clock = new MutableClock(Instant.parse("2026-07-27T00:00:00Z"));
        InMemoryDeliveries deliveries = new InMemoryDeliveries();
        NotificationDelivery pending = NotificationDelivery.pending("orphan", "email", "missing-alert",
            clock.instant().minusSeconds(60));
        deliveries.create(pending);
        deliveries.update(pending.claim(clock.instant().minusSeconds(60), clock.instant()), pending.getVersion());
        NotificationDeliveryService service = new NotificationDeliveryService("email", (id, value) -> { },
            deliveries, new StubAlerts(alert()), clock, 3, Duration.ofMinutes(1), Duration.ofMinutes(5));

        service.retryDue(10);

        assertEquals(NotificationDelivery.Status.FAILED, deliveries.only().getStatus());
        assertEquals("AGGREGATE_NOT_FOUND", deliveries.only().getFailureCategory());
    }

    @Test
    void continuesTheBatchAfterOneAlertLookupFails() {
        MutableClock clock = new MutableClock(Instant.parse("2026-07-27T00:00:00Z"));
        InMemoryDeliveries deliveries = new InMemoryDeliveries();
        addExpiredClaim(deliveries, "bad-delivery", "email", "bad-alert", clock.instant());
        addExpiredClaim(deliveries, "good-delivery", "email", "good-alert", clock.instant());
        SecurityAlert good = alert("good-alert");
        AlertRepository alerts = new StubAlerts(good) {
            @Override public Optional<SecurityAlert> findAlert(String id) {
                if ("bad-alert".equals(id)) throw new IllegalStateException("database row unavailable");
                return super.findAlert(id);
            }
        };
        AtomicInteger calls = new AtomicInteger();
        NotificationDeliveryService service = new NotificationDeliveryService("email",
            (id, value) -> calls.incrementAndGet(), deliveries, alerts, clock, 3,
            Duration.ofMinutes(1), Duration.ofMinutes(5));

        service.retryDue(10);

        assertEquals(1, calls.get());
        assertEquals(NotificationDelivery.Status.RETRY_PENDING,
            deliveries.find("email", "bad-alert").get().getStatus());
        assertEquals(NotificationDelivery.Status.DELIVERED,
            deliveries.find("email", "good-alert").get().getStatus());
    }

    private static void addExpiredClaim(InMemoryDeliveries deliveries, String id, String channel,
            String aggregateId, Instant now) {
        NotificationDelivery pending = NotificationDelivery.pending(id, channel, aggregateId,
            now.minusSeconds(60));
        deliveries.create(pending);
        deliveries.update(pending.claim(now.minusSeconds(60), now), pending.getVersion());
    }

    private static SecurityAlert alert() {
        return alert("alert-1");
    }

    private static SecurityAlert alert(String id) {
        Instant at = Instant.parse("2026-07-27T00:00:00Z");
        return new SecurityAlert(id, "AUTH-01", RiskLevel.HIGH, "fingerprint", "alice",
            AlertStatus.NEW, at, at, 1, 0);
    }

    private static final class InMemoryDeliveries implements NotificationDeliveryRepository {
        private final Map<String, NotificationDelivery> values = new LinkedHashMap<String, NotificationDelivery>();
        private String lastFailureCategory;
        @Override public Optional<NotificationDelivery> find(String channel, String aggregateId) {
            for (NotificationDelivery value : values.values()) if (value.getChannel().equals(channel)
                && value.getAggregateId().equals(aggregateId)) return Optional.of(value);
            return Optional.empty();
        }
        @Override public boolean create(NotificationDelivery delivery) {
            if (values.containsKey(delivery.getDeliveryId())) return false;
            values.put(delivery.getDeliveryId(), delivery); return true;
        }
        @Override public boolean update(NotificationDelivery delivery, long expectedVersion) {
            NotificationDelivery current = values.get(delivery.getDeliveryId());
            if (current == null || current.getVersion() != expectedVersion) return false;
            values.put(delivery.getDeliveryId(), delivery);
            if (delivery.getFailureCategory() != null) lastFailureCategory = delivery.getFailureCategory();
            return true;
        }
        @Override public List<NotificationDelivery> findDue(String channel, Instant at, int limit) {
            List<NotificationDelivery> result = new ArrayList<NotificationDelivery>();
            for (NotificationDelivery value : values.values()) if (value.isDueAt(channel, at)) result.add(value);
            return result.size() <= limit ? result : result.subList(0, limit);
        }
        NotificationDelivery only() { return values.values().iterator().next(); }
    }

    private static class StubAlerts implements AlertRepository {
        private final SecurityAlert alert;
        private int saveCalls;
        private StubAlerts(SecurityAlert alert) { this.alert = alert; }
        @Override public void save(SecurityAlert value) { saveCalls++; }
        @Override public Optional<SecurityAlert> findAlert(String id) { return alert.getAlertId().equals(id)
            ? Optional.of(alert) : Optional.<SecurityAlert>empty(); }
        @Override public Optional<SecurityAlert> findOpen(String fingerprint) { return Optional.empty(); }
        @Override public void linkEvent(String alertId, String eventId) { }
        @Override public void appendDisposition(AlertDisposition disposition) { }
        @Override public List<AlertDisposition> findDispositions(String alertId) { return Collections.emptyList(); }
    }

    private static final class MutableClock extends Clock {
        private Instant current;
        private MutableClock(Instant current) { this.current = current; }
        void advance(Duration duration) { current = current.plus(duration); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return current; }
    }
}

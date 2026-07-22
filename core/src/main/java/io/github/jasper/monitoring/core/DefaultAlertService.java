package io.github.jasper.monitoring.core;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

final class DefaultAlertService {
    private final MonitoringRepository repository;
    private final NotificationChannel notificationChannel;
    private final Clock clock;
    DefaultAlertService(MonitoringRepository repository, NotificationChannel notificationChannel, Clock clock) {
        this.repository = repository;
        this.notificationChannel = notificationChannel;
        this.clock = clock;
    }
    SecurityAlert raise(RuleMatch match, SecurityEvent event) {
        Optional<SecurityAlert> existing = repository.findOpenAlert(match.fingerprint());
        SecurityAlert alert = existing.isPresent()
            ? existing.get().observed(Instant.now(clock))
            : SecurityAlert.open(UUID.randomUUID().toString(), match, Instant.now(clock));
        repository.saveAlert(alert);
        repository.linkAlertEvent(alert.getAlertId(), event.getEventId());
        try {
            notificationChannel.notify(alert);
        } catch (RuntimeException ignored) {
            // A failed notification is retained by the surrounding monitoring data and cannot roll back business work.
        }
        return alert;
    }
}

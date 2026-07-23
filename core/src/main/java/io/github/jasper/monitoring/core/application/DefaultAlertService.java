package io.github.jasper.monitoring.core.application;

import io.github.jasper.monitoring.core.port.MonitoringRepository;
import io.github.jasper.monitoring.core.domain.SecurityAlert;
import io.github.jasper.monitoring.core.domain.SecurityEvent;
import io.github.jasper.monitoring.core.domain.RuleMatch;


import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

final class DefaultAlertService {
    private final MonitoringRepository repository;
    private final Clock clock;

    DefaultAlertService(MonitoringRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }
    SecurityAlert raise(RuleMatch match, SecurityEvent event) {
        Optional<SecurityAlert> existing = repository.findOpenAlert(match.fingerprint());
        SecurityAlert alert = existing.isPresent()
            ? existing.get().observed(Instant.now(clock))
            : SecurityAlert.open(UUID.randomUUID().toString(), match, Instant.now(clock));
        repository.saveAlert(alert);
        repository.linkAlertEvent(alert.getAlertId(), event.getEventId());
        return alert;
    }
}

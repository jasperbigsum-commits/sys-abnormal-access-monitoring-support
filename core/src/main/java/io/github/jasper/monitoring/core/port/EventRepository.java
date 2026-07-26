package io.github.jasper.monitoring.core.port;

import io.github.jasper.monitoring.core.domain.SecurityEvent;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Persistence boundary for immutable security events. */
public interface EventRepository {
    void save(SecurityEvent event);
    Optional<SecurityEvent> findEvent(String eventId);
    List<SecurityEvent> findSince(String systemId, Instant since);
}

package io.github.jasper.monitoring.core.port;

import io.github.jasper.monitoring.core.domain.AlertDisposition;
import io.github.jasper.monitoring.core.domain.SecurityAlert;
import java.util.List;
import java.util.Optional;

/** Persistence boundary for alert summaries and append-only dispositions. */
public interface AlertRepository {
    void save(SecurityAlert alert);
    Optional<SecurityAlert> findAlert(String alertId);
    Optional<SecurityAlert> findOpen(String fingerprint);
    void linkEvent(String alertId, String eventId);
    void appendDisposition(AlertDisposition disposition);
    List<AlertDisposition> findDispositions(String alertId);
}

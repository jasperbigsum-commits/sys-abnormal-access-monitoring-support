package io.github.jasper.monitoring.core.port;

import io.github.jasper.monitoring.core.domain.ControlRecord;
import java.util.Optional;

/** Persistence boundary for idempotent control execution records. */
public interface ControlRepository {
    Optional<ControlRecord> findControl(String idempotencyKey);
    void save(ControlRecord record);
}

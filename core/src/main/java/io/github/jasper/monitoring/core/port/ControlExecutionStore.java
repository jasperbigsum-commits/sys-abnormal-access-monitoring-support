package io.github.jasper.monitoring.core.port;

import io.github.jasper.monitoring.core.domain.ControlCommand;
import io.github.jasper.monitoring.core.domain.ControlExecution;
import io.github.jasper.monitoring.core.domain.control.ControlAttempt;
import java.util.Optional;

/** Narrow durable port for control reservations and append-only attempts. */
public interface ControlExecutionStore {
    Optional<ControlExecution> find(String idempotencyKey);
    /** Atomically creates PENDING reservation; false means another worker owns it. */
    boolean reserve(ControlCommand command);
    void appendAttempt(ControlAttempt attempt);
    void complete(String idempotencyKey, ControlExecution execution);
}

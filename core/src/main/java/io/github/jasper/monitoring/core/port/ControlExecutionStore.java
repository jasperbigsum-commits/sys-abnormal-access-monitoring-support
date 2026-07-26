package io.github.jasper.monitoring.core.port;

import io.github.jasper.monitoring.api.control.ControlStatus;
import io.github.jasper.monitoring.core.domain.ControlCommand;
import io.github.jasper.monitoring.core.domain.control.StoredControl;
import java.time.Instant;
import java.util.Optional;

/** Atomic persistence boundary for the control state machine. */
public interface ControlExecutionStore {
    Optional<StoredControl> find(String idempotencyKey);
    /** Creates the aggregate and first attempt atomically; false only for an existing idempotency key. */
    boolean reserve(ControlCommand command, ControlStatus initialStatus, Instant at);
    /** Version-guarded state transition and attempt append in one transaction. */
    StoredControl transition(String idempotencyKey, long expectedVersion, ControlStatus expectedStatus,
                             ControlStatus targetStatus, String reason, Instant at);
}

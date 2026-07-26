package io.github.jasper.monitoring.core.application.control;

import io.github.jasper.monitoring.api.ControlStatus;
import io.github.jasper.monitoring.api.control.ControlCatalog;
import io.github.jasper.monitoring.api.control.ControlType;
import io.github.jasper.monitoring.core.domain.ControlCommand;
import io.github.jasper.monitoring.core.domain.ControlExecution;
import io.github.jasper.monitoring.core.domain.control.ControlAttempt;
import io.github.jasper.monitoring.core.port.ControlExecutionStore;
import io.github.jasper.monitoring.core.port.ControlHandler;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/** Executes a durable control reservation without holding a database transaction over host code. */
public final class ControlExecutionService {
    private final ControlExecutionStore store;
    private final ControlCatalog<ControlHandler> catalog;
    private final Clock clock;

    public ControlExecutionService(ControlExecutionStore store, ControlCatalog<ControlHandler> catalog, Clock clock) {
        this.store = Objects.requireNonNull(store, "store");
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public ControlExecution execute(ControlCommand command) {
        Objects.requireNonNull(command, "command");
        Optional<ControlExecution> existing = store.find(command.getIdempotencyKey());
        if (existing.isPresent() && existing.get().getStatus() != ControlStatus.FAILED) return existing.get().replay();
        if (!store.reserve(command)) {
            return store.find(command.getIdempotencyKey()).map(ControlExecution::replay)
                .orElseGet(() -> ControlExecution.skipped(command.getIdempotencyKey(), "RESERVATION_CONFLICT"));
        }
        Instant now = Instant.now(clock);
        store.appendAttempt(new ControlAttempt(command.getIdempotencyKey(), 1,
            io.github.jasper.monitoring.api.control.ControlStatus.PENDING, null, now));
        ControlExecution result;
        try {
            ControlType type = ControlType.valueOf(command.getAction().name());
            ControlHandler handler = catalog.require(type);
            result = handler.execute(command);
            if (result == null) result = ControlExecution.failed(command.getIdempotencyKey(), "HANDLER_RETURNED_NULL");
        } catch (RuntimeException ex) {
            result = ControlExecution.failed(command.getIdempotencyKey(), "CONTROL_HANDLER_FAILED");
        }
        store.complete(command.getIdempotencyKey(), result);
        store.appendAttempt(new ControlAttempt(command.getIdempotencyKey(), 2,
            toStatus(result.getStatus()), result.getFailureReason(), Instant.now(clock)));
        return result;
    }

    private static io.github.jasper.monitoring.api.control.ControlStatus toStatus(ControlStatus status) {
        return io.github.jasper.monitoring.api.control.ControlStatus.valueOf(status.name());
    }
}

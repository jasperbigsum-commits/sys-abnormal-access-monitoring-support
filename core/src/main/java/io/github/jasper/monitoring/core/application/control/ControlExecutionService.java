package io.github.jasper.monitoring.core.application.control;

import io.github.jasper.monitoring.api.control.ControlCatalog;
import io.github.jasper.monitoring.api.control.ControlStatus;
import io.github.jasper.monitoring.api.control.ControlType;
import io.github.jasper.monitoring.core.domain.ControlCommand;
import io.github.jasper.monitoring.core.domain.ControlExecution;
import io.github.jasper.monitoring.core.domain.control.StoredControl;
import io.github.jasper.monitoring.core.port.ControlExecutionStore;
import io.github.jasper.monitoring.core.port.ControlHandler;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/** Durable control state machine. Host code is invoked only after PENDING commits. */
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
        ControlType type = ControlType.from(command.getAction());
        if (type.requiresApproval()) return createAwaitingApproval(command);
        return executeAutomatic(command, false);
    }

    public ControlExecution retry(ControlCommand command) { return executeAutomatic(command, true, null); }
    public ControlExecution retry(ControlCommand command, long expectedVersion) {
        return executeAutomatic(command, true, Long.valueOf(expectedVersion));
    }

    /** Recovers a committed PENDING reservation after a worker lost the terminal write. */
    public ControlExecution recover(ControlCommand command) {
        StoredControl pending = require(command.getIdempotencyKey(), ControlStatus.PENDING);
        return invoke(command, pending);
    }

    public ControlExecution approve(ControlCommand command) {
        return approve(command, null);
    }

    public ControlExecution approve(ControlCommand command, long expectedVersion) {
        return approve(command, Long.valueOf(expectedVersion));
    }

    private ControlExecution approve(ControlCommand command, Long expectedVersion) {
        StoredControl awaiting = require(command.getIdempotencyKey(), ControlStatus.AWAITING_APPROVAL);
        requireVersion(awaiting, expectedVersion);
        StoredControl pending = store.transition(command.getIdempotencyKey(), awaiting.version(),
            ControlStatus.AWAITING_APPROVAL, ControlStatus.PENDING, null, now());
        return invoke(command, pending);
    }

    public ControlExecution reject(String idempotencyKey, String reason) {
        return reject(idempotencyKey, reason, null);
    }

    public ControlExecution reject(String idempotencyKey, String reason, long expectedVersion) {
        return reject(idempotencyKey, reason, Long.valueOf(expectedVersion));
    }

    private ControlExecution reject(String idempotencyKey, String reason, Long expectedVersion) {
        StoredControl awaiting = require(idempotencyKey, ControlStatus.AWAITING_APPROVAL);
        requireVersion(awaiting, expectedVersion);
        return store.transition(idempotencyKey, awaiting.version(), ControlStatus.AWAITING_APPROVAL,
            ControlStatus.REJECTED, reason, now()).execution();
    }

    private ControlExecution createAwaitingApproval(ControlCommand command) {
        if (store.reserve(command, ControlStatus.AWAITING_APPROVAL, now()))
            return ControlExecution.awaitingApproval(command.getIdempotencyKey());
        return store.find(command.getIdempotencyKey()).get().execution().replay();
    }

    private ControlExecution executeAutomatic(ControlCommand command, boolean retry) {
        return executeAutomatic(command, retry, null);
    }

    private ControlExecution executeAutomatic(ControlCommand command, boolean retry, Long expectedVersion) {
        Optional<StoredControl> existing = store.find(command.getIdempotencyKey());
        StoredControl pending;
        if (!existing.isPresent()) {
            if (!store.reserve(command, ControlStatus.PENDING, now()))
                return store.find(command.getIdempotencyKey()).get().execution().replay();
            pending = store.find(command.getIdempotencyKey()).get();
        } else if (retry && existing.get().status() == ControlStatus.FAILED) {
            requireVersion(existing.get(), expectedVersion);
            pending = store.transition(command.getIdempotencyKey(), existing.get().version(), ControlStatus.FAILED,
                ControlStatus.PENDING, null, now());
        } else {
            return existing.get().execution().replay();
        }
        return invoke(command, pending);
    }

    private ControlExecution invoke(ControlCommand command, StoredControl pending) {
        ControlExecution result;
        try {
            result = catalog.require(ControlType.from(command.getAction())).execute(command);
            if (result == null) result = ControlExecution.failed(command.getIdempotencyKey(), "HANDLER_RETURNED_NULL");
        } catch (RuntimeException exception) {
            result = ControlExecution.failed(command.getIdempotencyKey(), "CONTROL_HANDLER_FAILED");
        }
        if (result.getStatus() != ControlStatus.SUCCEEDED && result.getStatus() != ControlStatus.FAILED
            && result.getStatus() != ControlStatus.SKIPPED)
            result = ControlExecution.failed(command.getIdempotencyKey(), "INVALID_HANDLER_STATUS");
        return store.transition(command.getIdempotencyKey(), pending.version(), ControlStatus.PENDING,
            result.getStatus(), result.getFailureReason(), now()).execution();
    }

    private StoredControl require(String key, ControlStatus status) {
        StoredControl value = store.find(key).orElseThrow(() -> new IllegalStateException("Unknown control: " + key));
        if (value.status() != status) throw new IllegalStateException("Expected " + status + " but was " + value.status());
        return value;
    }
    private static void requireVersion(StoredControl control, Long expectedVersion) {
        if (expectedVersion != null && control.version() != expectedVersion.longValue()) {
            throw new IllegalStateException("Control version changed");
        }
    }
    private Instant now() { return Instant.now(clock); }
}

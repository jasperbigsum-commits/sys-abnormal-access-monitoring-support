package io.github.jasper.monitoring.core;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

/**
 * Executes controls through host handlers and persists one outcome per idempotency key.
 * A repeated key returns the original outcome without invoking the host handler again.
 */
public final class DefaultControlService {
    private final MonitoringRepository repository;
    private final ControlHandlerRegistry handlers;
    private final Clock clock;
    /**
     * @param repository persistence port used for idempotency and audit records
     * @param handlers host implementations for concrete control actions
     * @param clock source of audit timestamps
     */
    public DefaultControlService(MonitoringRepository repository, ControlHandlerRegistry handlers, Clock clock) {
        this.repository = repository;
        this.handlers = handlers;
        this.clock = clock;
    }
    /**
     * Executes a command once or returns a replay-marked persisted result.
     * Handler exceptions are converted to a failed result so monitoring does not leak host details.
     *
     * @param command control instruction to execute
     * @return persisted execution outcome
     */
    public ControlExecution execute(ControlCommand command) {
        Optional<ControlRecord> existing = repository.findControl(command.getIdempotencyKey());
        if (existing.isPresent()) {
            return existing.get().getExecution().replay();
        }
        Optional<ControlHandler> handler = handlers.find(command.getAction());
        ControlExecution execution;
        if (!handler.isPresent()) {
            execution = ControlExecution.failed(command.getIdempotencyKey(), "No control handler for " + command.getAction());
        } else {
            try {
                execution = handler.get().execute(command);
                if (execution == null) {
                    execution = ControlExecution.failed(command.getIdempotencyKey(), "Control handler returned no result");
                }
            } catch (RuntimeException exception) {
                execution = ControlExecution.failed(command.getIdempotencyKey(), "Control handler failed");
            }
        }
        repository.saveControl(new ControlRecord(command, execution, Instant.now(clock)));
        return execution;
    }
}

package io.github.jasper.monitoring.core.application.management;

import io.github.jasper.monitoring.api.error.ManagementConflictException;
import io.github.jasper.monitoring.api.management.ControlManagementService;
import io.github.jasper.monitoring.api.management.ManagementActor;
import io.github.jasper.monitoring.api.management.ManagementOperation;
import io.github.jasper.monitoring.api.management.ManagementPage;
import io.github.jasper.monitoring.api.management.command.ControlApprovalCommand;
import io.github.jasper.monitoring.api.management.command.ControlExecutionCommand;
import io.github.jasper.monitoring.api.management.command.ControlRejectionCommand;
import io.github.jasper.monitoring.api.management.command.ControlRetryCommand;
import io.github.jasper.monitoring.api.management.command.VersionedReasonCommand;
import io.github.jasper.monitoring.api.management.model.ControlExecutionView;
import io.github.jasper.monitoring.api.management.model.ControlView;
import io.github.jasper.monitoring.api.management.query.ControlQuery;
import io.github.jasper.monitoring.core.application.control.ControlExecutionService;
import io.github.jasper.monitoring.core.domain.ControlCommand;
import io.github.jasper.monitoring.core.domain.ControlExecution;
import io.github.jasper.monitoring.core.port.ManagementQueryRepository;
import io.github.jasper.monitoring.core.port.MonitoringTransaction;
import java.util.Objects;

/** Authorized control lifecycle operations. Host execution remains owned by the durable control worker. */
public final class DefaultControlManagementService extends AbstractManagementService
        implements ControlManagementService {
    private final ControlExecutionService executions;

    public DefaultControlManagementService(ManagementAccessGuard access, ManagementQueryRepository queries,
                                           MonitoringTransaction transaction, ControlExecutionService executions) {
        super(access, queries, transaction);
        this.executions = Objects.requireNonNull(executions, "executions");
    }

    @Override
    public ManagementPage<ControlView> search(final ManagementActor actor, final ControlQuery query) {
        Objects.requireNonNull(query, "query");
        access.require(actor, ManagementOperation.CONTROL_READ, "control", "*");
        return transaction.required(() -> {
            ManagementPage<ControlView> page = queries.searchControls(actor.getSystemScope(), query);
            success(actor, ManagementOperation.CONTROL_READ, "control", "*");
            return page;
        });
    }

    @Override
    public ControlView get(final ManagementActor actor, final String id) {
        access.require(actor, ManagementOperation.CONTROL_READ, "control", id);
        return transaction.required(() -> {
            ControlView view = require(queries.findControlView(actor.getSystemScope(), id), "control", id);
            success(actor, ManagementOperation.CONTROL_READ, "control", id);
            return view;
        });
    }

    @Override
    public ControlView approve(ManagementActor actor, ControlApprovalCommand command) {
        return transition(actor, ManagementOperation.CONTROL_APPROVE, command, "AWAITING_APPROVAL", 0);
    }

    @Override
    public ControlView reject(ManagementActor actor, ControlRejectionCommand command) {
        return transition(actor, ManagementOperation.CONTROL_REJECT, command, "AWAITING_APPROVAL", 1);
    }

    @Override
    public ControlView retryFailed(ManagementActor actor, ControlRetryCommand command) {
        return transition(actor, ManagementOperation.CONTROL_RETRY, command, "FAILED", 2);
    }

    @Override
    public ControlExecutionView execute(final ManagementActor actor, final ControlExecutionCommand command) {
        Objects.requireNonNull(command, "command");
        final String id = command.getIdempotencyKey();
        access.require(actor, ManagementOperation.CONTROL_EXECUTE, "control", id);
        final ControlExecution execution = executions.execute(new ControlCommand(id, "manual-management",
            command.getSubject(), command.getAction(), command.getExpiresAt(), "MANUAL-CONTROL"));
        return transaction.required(() -> {
            success(actor, ManagementOperation.CONTROL_EXECUTE, "control", id);
            return ControlExecutionView.of(execution.getIdempotencyKey(), execution.getStatus().name(),
                execution.isIdempotentReplay());
        });
    }

    private ControlView transition(final ManagementActor actor, final ManagementOperation operation,
                                   final VersionedReasonCommand command, final String expected, final int action) {
        Objects.requireNonNull(command, "command");
        final String id = command.getResourceId();
        access.require(actor, operation, "control", id);
        ControlCommand control = transaction.required(() -> {
            ControlView current = require(queries.findControlView(actor.getSystemScope(), id), "control", id);
            if (current.getVersion() != command.getExpectedVersion() || !expected.equals(current.getStatus())) {
                throw new ManagementConflictException("Control state changed");
            }
            return require(queries.findControlCommand(actor.getSystemScope(), id), "control", id);
        });
        if (action == 0) {
            executions.approve(control, command.getExpectedVersion());
        } else if (action == 1) {
            executions.reject(control.getIdempotencyKey(), command.getReason(), command.getExpectedVersion());
        } else {
            executions.retry(control, command.getExpectedVersion());
        }
        return transaction.required(() -> {
            ControlView view = require(queries.findControlView(actor.getSystemScope(), id), "control", id);
            success(actor, operation, "control", id);
            return view;
        });
    }
}

package io.github.jasper.monitoring.core.application.management;

import io.github.jasper.monitoring.api.management.ControlManagementService;
import io.github.jasper.monitoring.api.management.ManagementActor;
import io.github.jasper.monitoring.api.management.ManagementOperation;
import io.github.jasper.monitoring.api.management.ManagementPage;
import io.github.jasper.monitoring.api.management.command.ControlApprovalCommand;
import io.github.jasper.monitoring.api.management.command.ControlRejectionCommand;
import io.github.jasper.monitoring.api.management.command.ControlRetryCommand;
import io.github.jasper.monitoring.api.management.command.VersionedReasonCommand;
import io.github.jasper.monitoring.api.management.model.ControlView;
import io.github.jasper.monitoring.api.management.query.ControlQuery;
import io.github.jasper.monitoring.core.port.ManagementQueryRepository;
import io.github.jasper.monitoring.core.port.MonitoringTransaction;
import java.util.Objects;

/** Authorized control lifecycle operations. Host execution remains owned by the durable control worker. */
public final class DefaultControlManagementService extends AbstractManagementService implements ControlManagementService {
    public DefaultControlManagementService(ManagementAccessGuard access, ManagementQueryRepository queries,
                                           MonitoringTransaction transaction) { super(access, queries, transaction); }
    @Override public ManagementPage<ControlView> search(final ManagementActor actor, final ControlQuery query) {
        Objects.requireNonNull(query, "query"); access.require(actor, ManagementOperation.CONTROL_READ, "control", "*");
        return transaction.required(() -> { ManagementPage<ControlView> page = queries.searchControls(actor.getSystemScope(), query);
            success(actor, ManagementOperation.CONTROL_READ, "control", "*"); return page; });
    }
    @Override public ControlView get(final ManagementActor actor, final String id) {
        access.require(actor, ManagementOperation.CONTROL_READ, "control", id);
        return transaction.required(() -> { ControlView view = require(queries.findControlView(actor.getSystemScope(), id), "control", id);
            success(actor, ManagementOperation.CONTROL_READ, "control", id); return view; });
    }
    @Override public ControlView approve(ManagementActor actor, ControlApprovalCommand command) {
        return change(actor, ManagementOperation.CONTROL_APPROVE, command, "AWAITING_APPROVAL", "PENDING");
    }
    @Override public ControlView reject(ManagementActor actor, ControlRejectionCommand command) {
        return change(actor, ManagementOperation.CONTROL_REJECT, command, "AWAITING_APPROVAL", "REJECTED");
    }
    @Override public ControlView retryFailed(ManagementActor actor, ControlRetryCommand command) {
        return change(actor, ManagementOperation.CONTROL_RETRY, command, "FAILED", "PENDING");
    }
    private ControlView change(final ManagementActor actor, final ManagementOperation operation,
                               final VersionedReasonCommand command, final String expected, final String target) {
        Objects.requireNonNull(command, "command"); final String id = command.getResourceId();
        access.require(actor, operation, "control", id);
        return transaction.required(() -> { requireUpdated(queries.transitionControl(actor.getSystemScope(), id,
                command.getExpectedVersion(), expected, target, command.getReason()));
            ControlView view = require(queries.findControlView(actor.getSystemScope(), id), "control", id);
            success(actor, operation, "control", id); return view; });
    }
}

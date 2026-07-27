package io.github.jasper.monitoring.api.management;

import io.github.jasper.monitoring.api.management.command.ControlApprovalCommand;
import io.github.jasper.monitoring.api.management.command.ControlExecutionCommand;
import io.github.jasper.monitoring.api.management.command.ControlRejectionCommand;
import io.github.jasper.monitoring.api.management.command.ControlRetryCommand;
import io.github.jasper.monitoring.api.management.model.ControlExecutionView;
import io.github.jasper.monitoring.api.management.model.ControlView;
import io.github.jasper.monitoring.api.management.query.ControlQuery;

/**
 * Authorized control lifecycle boundary for direct use by host Controller adapters.
 * Authorization always precedes state access or host side effects.
 */
public interface ControlManagementService {
    ManagementPage<ControlView> search(ManagementActor actor, ControlQuery query);

    ControlView get(ManagementActor actor, String id);

    ControlView approve(ManagementActor actor, ControlApprovalCommand command);

    ControlView reject(ManagementActor actor, ControlRejectionCommand command);

    ControlView retryFailed(ManagementActor actor, ControlRetryCommand command);

    /** Authorizes, persists and executes a new manual control such as an administrative session revocation. */
    ControlExecutionView execute(ManagementActor actor, ControlExecutionCommand command);
}

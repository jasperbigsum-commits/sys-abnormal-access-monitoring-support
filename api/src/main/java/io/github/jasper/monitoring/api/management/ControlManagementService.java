package io.github.jasper.monitoring.api.management;
import io.github.jasper.monitoring.api.management.command.*; import io.github.jasper.monitoring.api.management.model.ControlView; import io.github.jasper.monitoring.api.management.query.*;
/** Control approval boundary. Authorization precedes access; state/audit writes are transactional and host effects occur after commit. */
public interface ControlManagementService {
    ManagementPage<ControlView> search(ManagementActor actor, ControlQuery query);
    ControlView get(ManagementActor actor, String id);
    ControlView approve(ManagementActor actor, ControlApprovalCommand command);
    ControlView reject(ManagementActor actor, ControlRejectionCommand command);
    ControlView retryFailed(ManagementActor actor, ControlRetryCommand command);
}

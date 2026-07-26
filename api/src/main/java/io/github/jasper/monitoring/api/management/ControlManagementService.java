package io.github.jasper.monitoring.api.management;
import io.github.jasper.monitoring.api.management.command.*; import io.github.jasper.monitoring.api.management.model.ControlView; import io.github.jasper.monitoring.api.management.query.*;
/** Control approval boundary. Authorization precedes access; state/audit writes are transactional and host effects occur after commit. */
public interface ControlManagementService {
    ManagementPage<ControlView> search(ManagementAuthorizer authorizer, ManagementActor actor, ControlQuery query);
    ControlView get(ManagementAuthorizer authorizer, ManagementActor actor, String id);
    ControlView approve(ManagementAuthorizer authorizer, ManagementActor actor, ControlApprovalCommand command);
    ControlView reject(ManagementAuthorizer authorizer, ManagementActor actor, ControlRejectionCommand command);
    ControlView retryFailed(ManagementAuthorizer authorizer, ManagementActor actor, ControlRetryCommand command);
}

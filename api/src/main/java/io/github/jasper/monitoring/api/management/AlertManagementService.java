package io.github.jasper.monitoring.api.management;
import io.github.jasper.monitoring.api.management.command.*; import io.github.jasper.monitoring.api.management.model.AlertView; import io.github.jasper.monitoring.api.management.query.*;
/** Alert queries and versioned disposition commands. Every method requires authorization in actor scope; mutations are transactional and replay-safe. */
public interface AlertManagementService {
    ManagementPage<AlertView> search(ManagementAuthorizer authorizer, ManagementActor actor, AlertQuery query);
    AlertView get(ManagementAuthorizer authorizer, ManagementActor actor, String alertId);
    AlertView acknowledge(ManagementAuthorizer authorizer, ManagementActor actor, AlertAcknowledgeCommand command);
    AlertView startInvestigation(ManagementAuthorizer authorizer, ManagementActor actor, AlertStartInvestigationCommand command);
    AlertView close(ManagementAuthorizer authorizer, ManagementActor actor, AlertCloseCommand command);
    AlertView markFalsePositive(ManagementAuthorizer authorizer, ManagementActor actor, AlertFalsePositiveCommand command);
}

package io.github.jasper.monitoring.api.management;
import io.github.jasper.monitoring.api.management.command.*; import io.github.jasper.monitoring.api.management.model.AlertView; import io.github.jasper.monitoring.api.management.query.*;
/** Alert queries and versioned disposition commands. Every method requires authorization in actor scope; mutations are transactional and replay-safe. */
public interface AlertManagementService {
    ManagementPage<AlertView> search(ManagementActor actor, AlertQuery query);
    AlertView get(ManagementActor actor, String alertId);
    AlertView acknowledge(ManagementActor actor, AlertAcknowledgeCommand command);
    AlertView startInvestigation(ManagementActor actor, AlertStartInvestigationCommand command);
    AlertView close(ManagementActor actor, AlertCloseCommand command);
    AlertView markFalsePositive(ManagementActor actor, AlertFalsePositiveCommand command);
}

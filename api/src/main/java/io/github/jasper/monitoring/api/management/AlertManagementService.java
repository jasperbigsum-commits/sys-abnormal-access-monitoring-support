package io.github.jasper.monitoring.api.management;
import io.github.jasper.monitoring.api.management.command.*; import io.github.jasper.monitoring.api.management.model.AlertView; import io.github.jasper.monitoring.api.management.query.*;
/**
 * Alert queries and versioned disposition commands for direct use by a host Controller adapter.
 * Every method authorizes the actor scope before persistence access. Mutations use optimistic locking,
 * append disposition history, and commit their success audit in the same transaction.
 */
public interface AlertManagementService {
    /** Lists alerts visible in the actor's system scope. */
    ManagementPage<AlertView> search(ManagementActor actor, AlertQuery query);
    /** Reads one alert after scoped authorization. */
    AlertView get(ManagementActor actor, String alertId);
    /** Returns append-only assignment history with a caller-supplied bounded page. */
    ManagementPage<io.github.jasper.monitoring.api.management.model.AlertAssignmentView> assignmentHistory(
        ManagementActor actor, String alertId,
        io.github.jasper.monitoring.api.management.query.AlertAssignmentQuery query);
    /** Assigns an alert for triage, begins investigation, and appends the assignee to disposition history. */
    AlertView assign(ManagementActor actor, AlertAssignmentCommand command);
    /** Records acknowledgement without replacing earlier dispositions. */
    AlertView acknowledge(ManagementActor actor, AlertAcknowledgeCommand command);
    /** Moves an unassigned alert into investigation without assigning an operator. */
    AlertView startInvestigation(ManagementActor actor, AlertStartInvestigationCommand command);
    /** Closes an alert using its expected version. */
    AlertView close(ManagementActor actor, AlertCloseCommand command);
    /** Closes an alert as a false positive while retaining all evidence and disposition history. */
    AlertView markFalsePositive(ManagementActor actor, AlertFalsePositiveCommand command);
}

package io.github.jasper.monitoring.core.application.management;

import io.github.jasper.monitoring.api.management.AlertManagementService;
import io.github.jasper.monitoring.api.management.ManagementActor;
import io.github.jasper.monitoring.api.management.ManagementOperation;
import io.github.jasper.monitoring.api.management.ManagementPage;
import io.github.jasper.monitoring.api.management.command.AlertAcknowledgeCommand;
import io.github.jasper.monitoring.api.management.command.AlertCloseCommand;
import io.github.jasper.monitoring.api.management.command.AlertFalsePositiveCommand;
import io.github.jasper.monitoring.api.management.command.AlertStartInvestigationCommand;
import io.github.jasper.monitoring.api.management.command.VersionedReasonCommand;
import io.github.jasper.monitoring.api.management.model.AlertView;
import io.github.jasper.monitoring.api.management.query.AlertQuery;
import io.github.jasper.monitoring.core.port.ManagementQueryRepository;
import io.github.jasper.monitoring.core.port.MonitoringTransaction;
import java.util.Objects;

/** Authorized alert lifecycle operations with optimistic locking and append-only audit. */
public final class DefaultAlertManagementService extends AbstractManagementService implements AlertManagementService {
    public DefaultAlertManagementService(ManagementAccessGuard access, ManagementQueryRepository queries,
                                         MonitoringTransaction transaction) { super(access, queries, transaction); }
    @Override public ManagementPage<AlertView> search(final ManagementActor actor, final AlertQuery query) {
        Objects.requireNonNull(query, "query");
        access.require(actor, ManagementOperation.ALERT_READ, "alert", "*");
        return transaction.required(() -> { ManagementPage<AlertView> page = queries.searchAlerts(actor.getSystemScope(), query);
            success(actor, ManagementOperation.ALERT_READ, "alert", "*"); return page; });
    }
    @Override public AlertView get(final ManagementActor actor, final String alertId) {
        access.require(actor, ManagementOperation.ALERT_READ, "alert", alertId);
        return transaction.required(() -> { AlertView view = require(queries.findAlertView(actor.getSystemScope(), alertId), "alert", alertId);
            success(actor, ManagementOperation.ALERT_READ, "alert", alertId); return view; });
    }
    @Override public AlertView acknowledge(ManagementActor actor, AlertAcknowledgeCommand command) {
        return change(actor, ManagementOperation.ALERT_ACKNOWLEDGE, command.getAlertId(), command.getExpectedVersion(),
            command.getReason(), command.getIdempotencyKey(), "ACKNOWLEDGED");
    }
    @Override public AlertView startInvestigation(ManagementActor actor, AlertStartInvestigationCommand command) {
        return change(actor, ManagementOperation.ALERT_INVESTIGATE, command, "IN_PROGRESS");
    }
    @Override public AlertView close(ManagementActor actor, AlertCloseCommand command) {
        return change(actor, ManagementOperation.ALERT_CLOSE, command, "CLOSED");
    }
    @Override public AlertView markFalsePositive(ManagementActor actor, AlertFalsePositiveCommand command) {
        return change(actor, ManagementOperation.ALERT_MARK_FALSE_POSITIVE, command, "FALSE_POSITIVE");
    }
    private AlertView change(ManagementActor actor, ManagementOperation operation, VersionedReasonCommand command,
                             String status) {
        Objects.requireNonNull(command, "command");
        return change(actor, operation, command.getResourceId(), command.getExpectedVersion(), command.getReason(),
            command.getIdempotencyKey(), status);
    }
    private AlertView change(final ManagementActor actor, final ManagementOperation operation, final String id,
                             final long version, final String reason, final String dispositionId, final String status) {
        access.require(actor, operation, "alert", id);
        return transaction.required(() -> { requireUpdated(queries.transitionAlert(actor.getSystemScope(), id, version,
                status, actor.getActorId(), reason, dispositionId));
            AlertView view = require(queries.findAlertView(actor.getSystemScope(), id), "alert", id);
            success(actor, operation, "alert", id); return view; });
    }
}

package io.github.jasper.monitoring.audit.spring2.management;

import io.github.jasper.monitoring.api.MonitoringContextAccessor;
import io.github.jasper.monitoring.api.management.AlertManagementService;
import io.github.jasper.monitoring.api.management.ManagementActor;
import io.github.jasper.monitoring.api.management.command.AlertAcknowledgeCommand;
import io.github.jasper.monitoring.api.management.command.AlertAssignmentCommand;
import io.github.jasper.monitoring.api.management.command.AlertCloseCommand;
import io.github.jasper.monitoring.api.management.command.AlertStartInvestigationCommand;
import io.github.jasper.monitoring.api.management.model.AlertView;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** HTTP adapter for the versioned alert disposition workflow. */
@RestController
@RequestMapping("/audit/management/alerts")
public class AlertManagementController {
    private final MonitoringContextAccessor contexts;
    private final AlertManagementService alerts;

    public AlertManagementController(MonitoringContextAccessor contexts, AlertManagementService alerts) {
        this.contexts = contexts;
        this.alerts = alerts;
    }

    @PostMapping("/{id}/acknowledge")
    public AlertView acknowledge(@PathVariable("id") String id, @RequestBody AlertRequest request) {
        return alerts.acknowledge(actor(), AlertAcknowledgeCommand.of(id, request.getExpectedVersion(),
            request.getReason(), request.getIdempotencyKey()));
    }

    @PostMapping("/{id}/assign")
    public AlertView assign(@PathVariable("id") String id, @RequestBody AlertRequest request) {
        return alerts.assign(actor(), AlertAssignmentCommand.of(id, request.getExpectedVersion(),
            request.getAssigneeId(), request.getReason(), request.getIdempotencyKey()));
    }

    @PostMapping("/{id}/investigate")
    public AlertView investigate(@PathVariable("id") String id, @RequestBody AlertRequest request) {
        return alerts.startInvestigation(actor(), AlertStartInvestigationCommand.of(id,
            request.getExpectedVersion(), request.getReason()));
    }

    @PostMapping("/{id}/close")
    public AlertView close(@PathVariable("id") String id, @RequestBody AlertRequest request) {
        return alerts.close(actor(), AlertCloseCommand.of(id, request.getExpectedVersion(), request.getReason()));
    }

    private ManagementActor actor() {
        return ManagementActor.of(contexts.identityContext().getUserId(), "audit-spring2-web");
    }

    public static final class AlertRequest {
        private long expectedVersion;
        private String reason;
        private String idempotencyKey;
        private String assigneeId;
        public long getExpectedVersion() { return expectedVersion; }
        public void setExpectedVersion(long value) { expectedVersion = value; }
        public String getReason() { return reason; }
        public void setReason(String value) { reason = value; }
        public String getIdempotencyKey() { return idempotencyKey; }
        public void setIdempotencyKey(String value) { idempotencyKey = value; }
        public String getAssigneeId() { return assigneeId; }
        public void setAssigneeId(String value) { assigneeId = value; }
    }
}

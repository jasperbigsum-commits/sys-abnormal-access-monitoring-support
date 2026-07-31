package io.github.jasper.monitoring.audit.spring2.management;

import io.github.jasper.monitoring.api.MonitoringContextAccessor;
import io.github.jasper.monitoring.api.management.AlertManagementService;
import io.github.jasper.monitoring.api.management.ManagementActor;
import io.github.jasper.monitoring.api.management.command.AlertAcknowledgeCommand;
import io.github.jasper.monitoring.api.management.command.AlertAssignmentCommand;
import io.github.jasper.monitoring.api.management.command.AlertCloseCommand;
import io.github.jasper.monitoring.api.management.command.AlertFalsePositiveCommand;
import io.github.jasper.monitoring.api.management.command.AlertStartInvestigationCommand;
import io.github.jasper.monitoring.api.management.ManagementPage;
import io.github.jasper.monitoring.api.management.query.AlertAssignmentQuery;
import io.github.jasper.monitoring.api.management.query.AlertQuery;
import io.github.jasper.monitoring.api.management.model.AlertAssignmentView;
import io.github.jasper.monitoring.api.management.model.AlertView;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 告警处置工作流的 HTTP 适配器。
 *
 * <p>请求参数只表达处置操作，操作者从服务端身份派生；授权、版本校验、处置历史和管理审计由
 * 公开管理服务完成。</p>
 */
@RestController
@RequestMapping("/audit/management/alerts")
public class AlertManagementController {
    private final MonitoringContextAccessor contexts;
    private final AlertManagementService alerts;

    public AlertManagementController(MonitoringContextAccessor contexts, AlertManagementService alerts) {
        this.contexts = contexts;
        this.alerts = alerts;
    }

    @GetMapping
    public ManagementResult<ManagementPage<AlertView>> search(@RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "size", required = false) Integer size) {
        return ManagementResult.ok(alerts.search(actor(), AlertQuery.of(
            ManagementHttpParameters.page(page, size, AlertQuery.Sort.CREATED_AT))));
    }

    @GetMapping("/{id}")
    public ManagementResult<AlertView> get(@PathVariable("id") String id) {
        return ManagementResult.ok(alerts.get(actor(), id));
    }

    @GetMapping("/{id}/assignments")
    public ManagementResult<ManagementPage<AlertAssignmentView>> assignments(@PathVariable("id") String id,
        @RequestParam(value = "page", required = false) Integer page, @RequestParam(value = "size", required = false) Integer size) {
        return ManagementResult.ok(alerts.assignmentHistory(actor(), id, AlertAssignmentQuery.of(
            ManagementHttpParameters.page(page, size, AlertAssignmentQuery.Sort.CREATED_AT))));
    }

    @PostMapping("/{id}/acknowledge")
    public ManagementResult<AlertView> acknowledge(@PathVariable("id") String id, @RequestBody AlertRequest request) {
        return ManagementResult.ok(alerts.acknowledge(actor(), AlertAcknowledgeCommand.of(id, request.getExpectedVersion(),
            request.getReason(), request.getIdempotencyKey())));
    }

    @PostMapping("/{id}/assign")
    public ManagementResult<AlertView> assign(@PathVariable("id") String id, @RequestBody AlertRequest request) {
        return ManagementResult.ok(alerts.assign(actor(), AlertAssignmentCommand.of(id, request.getExpectedVersion(),
            request.getAssigneeId(), request.getReason(), request.getIdempotencyKey())));
    }

    @PostMapping("/{id}/investigate")
    public ManagementResult<AlertView> investigate(@PathVariable("id") String id, @RequestBody AlertRequest request) {
        return ManagementResult.ok(alerts.startInvestigation(actor(), AlertStartInvestigationCommand.of(id,
            request.getExpectedVersion(), request.getReason())));
    }

    @PostMapping("/{id}/close")
    public ManagementResult<AlertView> close(@PathVariable("id") String id, @RequestBody AlertRequest request) {
        return ManagementResult.ok(alerts.close(actor(), AlertCloseCommand.of(id, request.getExpectedVersion(), request.getReason())));
    }

    @PostMapping("/{id}/false-positive")
    public ManagementResult<AlertView> falsePositive(@PathVariable("id") String id, @RequestBody AlertRequest request) {
        return ManagementResult.ok(alerts.markFalsePositive(actor(), AlertFalsePositiveCommand.of(id,
            request.getExpectedVersion(), request.getReason())));
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

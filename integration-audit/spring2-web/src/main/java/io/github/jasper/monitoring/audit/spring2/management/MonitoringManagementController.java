package io.github.jasper.monitoring.audit.spring2.management;

import io.github.jasper.monitoring.api.MonitoringContextAccessor;
import io.github.jasper.monitoring.api.management.ManagementActor;
import io.github.jasper.monitoring.api.management.SecurityEventQueryService;
import io.github.jasper.monitoring.api.management.ControlManagementService;
import io.github.jasper.monitoring.api.management.AlertManagementService;
import io.github.jasper.monitoring.api.management.ManagementPage;
import io.github.jasper.monitoring.api.management.command.ControlApprovalCommand;
import io.github.jasper.monitoring.api.management.command.ControlExecutionCommand;
import io.github.jasper.monitoring.api.management.command.ControlRejectionCommand;
import io.github.jasper.monitoring.api.management.command.ControlRetryCommand;
import io.github.jasper.monitoring.api.management.model.ControlExecutionView;
import io.github.jasper.monitoring.api.management.model.ControlView;
import io.github.jasper.monitoring.api.management.model.SecurityEventView;
import io.github.jasper.monitoring.api.management.query.ControlQuery;
import io.github.jasper.monitoring.api.management.query.AlertQuery;
import io.github.jasper.monitoring.api.management.query.SecurityEventQuery;
import io.github.jasper.monitoring.api.management.model.AlertView;
import java.time.Instant;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import io.github.jasper.monitoring.api.ControlActionType;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 公开管理服务契约的 HTTP 适配器。
 *
 * <p>只把服务端身份转换为 {@code ManagementActor} 并调用管理服务；系统范围授权、版本、事务和
 * 管理审计由组件管理服务负责，请求体不能选择操作者或授权范围。</p>
 */
@RestController
@RequestMapping("/audit/management")
public class MonitoringManagementController {
    private final MonitoringContextAccessor contexts;
    private final SecurityEventQueryService events;
    private final ControlManagementService controls;
    private final AlertManagementService alerts;
    private final JdbcTemplate jdbc;

    public MonitoringManagementController(MonitoringContextAccessor contexts,
                                          SecurityEventQueryService events, ControlManagementService controls,
                                          AlertManagementService alerts, JdbcTemplate jdbc) {
        this.contexts = contexts;
        this.events = events;
        this.controls = controls;
        this.alerts = alerts;
        this.jdbc = jdbc;
    }

    @GetMapping("/dashboard")
    public ManagementResult<Map<String, Object>> dashboard(
        @RequestParam(value = "from", required = false) String from, @RequestParam(value = "to", required = false) String to) {
        ManagementActor actor = actor();
        ManagementPage<AlertView> alertPage = alerts.search(actor, AlertQuery.of(
            ManagementHttpParameters.page(Integer.valueOf(0), Integer.valueOf(200), AlertQuery.Sort.CREATED_AT)));
        ManagementPage<SecurityEventView> eventPage = events.search(actor, SecurityEventQuery.of(
            ManagementHttpParameters.page(Integer.valueOf(0), Integer.valueOf(1), SecurityEventQuery.Sort.OCCURRED_AT),
            ManagementHttpParameters.from(from), ManagementHttpParameters.to(to)));
        Map<String, Object> metrics = new LinkedHashMap<String, Object>();
        metrics.put("openAlerts", Long.valueOf(openAlertCount(alertPage)));
        metrics.put("eventsToday", Long.valueOf(eventPage.getTotalElements()));
        metrics.put("highRiskSubjects", Integer.valueOf(0));
        metrics.put("controlSuccessRate", Integer.valueOf(0));
        Map<String, Object> riskDistribution = new LinkedHashMap<String, Object>();
        for (String risk : Arrays.asList("CRITICAL", "HIGH", "MEDIUM", "LOW")) riskDistribution.put(risk, Integer.valueOf(0));
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("metrics", metrics);
        result.put("riskTrend", java.util.Collections.emptyList());
        result.put("riskDistribution", riskDistribution);
        result.put("ruleContribution", java.util.Collections.emptyList());
        result.put("priorityAlertIds", priorityAlertIds(alertPage));
        return ManagementResult.ok(result);
    }

    @GetMapping("/audit-log")
    public ManagementResult<ManagementPage<Map<String, Object>>> auditLog(
        @RequestParam(value = "page", required = false) Integer page, @RequestParam(value = "size", required = false) Integer size,
        @RequestParam(value = "from", required = false) String from, @RequestParam(value = "to", required = false) String to,
        @RequestParam(value = "outcome", required = false) String outcome, @RequestParam(value = "operation", required = false) String operation,
        @RequestParam(value = "actorId", required = false) String actorId) {
        ManagementActor actor = actor();
        events.search(actor, SecurityEventQuery.of(ManagementHttpParameters.page(0, 1, SecurityEventQuery.Sort.OCCURRED_AT),
            ManagementHttpParameters.from(from), ManagementHttpParameters.to(to)));
        Instant fromAt = ManagementHttpParameters.from(from);
        Instant toAt = ManagementHttpParameters.to(to);
        int resolvedPage = page == null ? 0 : page.intValue();
        int resolvedSize = size == null ? 20 : size.intValue();
        if (resolvedPage < 0 || resolvedSize < 1 || resolvedSize > 200) throw new IllegalArgumentException("invalid page request");
        StringBuilder where = new StringBuilder(" WHERE system_id=? AND occurred_at>=? AND occurred_at<=?");
        java.util.List<Object> parameters = new ArrayList<Object>();
        parameters.add(actor.getSystemScope()); parameters.add(Timestamp.from(fromAt)); parameters.add(Timestamp.from(toAt));
        appendAuditFilter(where, parameters, "outcome", outcome);
        appendAuditFilter(where, parameters, "action", operation);
        appendAuditFilter(where, parameters, "actor_id", actorId);
        long total = jdbc.queryForObject("SELECT COUNT(*) FROM monitoring_management_audit" + where, parameters.toArray(), Long.class).longValue();
        java.util.List<Object> rowParameters = new ArrayList<Object>(parameters);
        rowParameters.add(Integer.valueOf(resolvedSize)); rowParameters.add(Integer.valueOf(resolvedPage * resolvedSize));
        java.util.List<Map<String, Object>> rows = jdbc.query("SELECT audit_id,actor_id,action,target_type,target_id,outcome,occurred_at FROM monitoring_management_audit"
            + where + " ORDER BY occurred_at DESC,audit_id DESC LIMIT ? OFFSET ?", rowParameters.toArray(), (rs, index) -> {
                Map<String, Object> row = new LinkedHashMap<String, Object>();
                row.put("id", rs.getString("audit_id")); row.put("actorId", rs.getString("actor_id")); row.put("operation", rs.getString("action"));
                row.put("targetType", rs.getString("target_type")); row.put("targetId", rs.getString("target_id")); row.put("outcome", rs.getString("outcome"));
                row.put("occurredAt", rs.getTimestamp("occurred_at").toInstant().toString()); row.put("requestId", rs.getString("audit_id"));
                return row;
            });
        return ManagementResult.ok(ManagementPage.of(rows, resolvedPage, resolvedSize, total));
    }

    @GetMapping("/events")
    public ManagementResult<ManagementPage<SecurityEventView>> events(
        @RequestParam(value = "page", required = false) Integer page, @RequestParam(value = "size", required = false) Integer size,
        @RequestParam(value = "from", required = false) String from, @RequestParam(value = "to", required = false) String to) {
        SecurityEventQuery query = SecurityEventQuery.of(
            ManagementHttpParameters.page(page, size, SecurityEventQuery.Sort.OCCURRED_AT),
            ManagementHttpParameters.from(from), ManagementHttpParameters.to(to));
        return ManagementResult.ok(events.search(actor(), query));
    }

    @GetMapping("/events/{id}")
    public ManagementResult<SecurityEventView> event(@PathVariable("id") String id) { return ManagementResult.ok(events.get(actor(), id)); }

    @GetMapping("/controls")
    public ManagementResult<ManagementPage<ControlView>> controls(
        @RequestParam(value = "page", required = false) Integer page, @RequestParam(value = "size", required = false) Integer size,
        @RequestParam(value = "from", required = false) String from, @RequestParam(value = "to", required = false) String to) {
        return ManagementResult.ok(controls.search(actor(), ControlQuery.of(
            ManagementHttpParameters.page(page, size, ControlQuery.Sort.CREATED_AT),
            ManagementHttpParameters.from(from), ManagementHttpParameters.to(to))));
    }

    @GetMapping("/controls/{id}")
    public ManagementResult<ControlView> control(@PathVariable("id") String id) { return ManagementResult.ok(controls.get(actor(), id)); }

    @PostMapping("/controls/{id}/approve")
    public ManagementResult<ControlView> approve(@PathVariable("id") String id, @RequestBody VersionedReasonRequest request) {
        ControlApprovalCommand command = request.getPassExpiresAt() == null
            ? ControlApprovalCommand.of(id, request.getExpectedVersion(), request.getReason())
            : ControlApprovalCommand.withPassUntil(id, request.getExpectedVersion(), request.getReason(),
                request.getPassExpiresAt());
        return ManagementResult.ok(controls.approve(actor(), command));
    }

    @PostMapping("/controls/{id}/reject")
    public ManagementResult<ControlView> reject(@PathVariable("id") String id, @RequestBody VersionedReasonRequest request) {
        return ManagementResult.ok(controls.reject(actor(), ControlRejectionCommand.of(id, request.getExpectedVersion(), request.getReason())));
    }

    @PostMapping("/controls/{id}/retry")
    public ManagementResult<ControlView> retry(@PathVariable("id") String id, @RequestBody VersionedReasonRequest request) {
        return ManagementResult.ok(controls.retryFailed(actor(), ControlRetryCommand.of(id, request.getExpectedVersion(), request.getReason())));
    }

    @PostMapping("/controls/execute")
    public ManagementResult<ControlExecutionView> execute(@RequestBody ControlExecutionRequest request) {
        Instant expiresAt = Instant.now().plusSeconds(Math.max(1, request.getTtlMinutes()) * 60L);
        return ManagementResult.ok(controls.execute(actor(), ControlExecutionCommand.of(request.getIdempotencyKey(), request.getSubject(),
            ControlActionType.valueOf(request.getAction()), expiresAt)));
    }

    @PostMapping("/sessions/{userId}/revoke")
    public ManagementResult<ControlExecutionView> revokeSessions(@PathVariable("userId") String userId,
                                              @RequestBody SessionRevokeRequest request) {
        ControlExecutionView execution = controls.execute(actor(), ControlExecutionCommand.of(request.getIdempotencyKey(),
            userId, ControlActionType.REVOKE_SESSION, Instant.now().plusSeconds(300)));
        return ManagementResult.ok(execution);
    }

    private ManagementActor actor() {
        return ManagementActor.of(contexts.identityContext().getUserId(), "audit-spring2-web");
    }

    public static final class SessionRevokeRequest {
        private String idempotencyKey;
        public String getIdempotencyKey() { return idempotencyKey; }
        public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
    }

    private long openAlertCount(ManagementPage<AlertView> alerts) {
        long open = 0;
        for (AlertView alert : alerts.getItems()) {
            if (!"CLOSED".equals(alert.getStatus()) && !"FALSE_POSITIVE".equals(alert.getStatus())) open++;
        }
        return open;
    }

    private java.util.List<String> priorityAlertIds(ManagementPage<AlertView> alerts) {
        java.util.List<String> ids = new java.util.ArrayList<String>();
        for (AlertView alert : alerts.getItems()) {
            if (!"CLOSED".equals(alert.getStatus()) && !"FALSE_POSITIVE".equals(alert.getStatus())) ids.add(alert.getId());
        }
        return ids;
    }

    private void appendAuditFilter(StringBuilder where, java.util.List<Object> parameters, String column, String value) {
        if (value == null || value.trim().isEmpty()) return;
        where.append(" AND ").append(column).append("=?");
        parameters.add(value.trim());
    }

    public static final class VersionedReasonRequest {
        private long expectedVersion; private String reason; private java.time.Instant passExpiresAt;
        public long getExpectedVersion() { return expectedVersion; }
        public void setExpectedVersion(long value) { expectedVersion = value; }
        public String getReason() { return reason; }
        public void setReason(String value) { reason = value; }
        public java.time.Instant getPassExpiresAt() { return passExpiresAt; }
        public void setPassExpiresAt(java.time.Instant value) { passExpiresAt = value; }
    }

    public static final class ControlExecutionRequest {
        private String subject; private String action; private int ttlMinutes; private String reason; private String idempotencyKey;
        public String getSubject() { return subject; }
        public void setSubject(String value) { subject = value; }
        public String getAction() { return action; }
        public void setAction(String value) { action = value; }
        public int getTtlMinutes() { return ttlMinutes; }
        public void setTtlMinutes(int value) { ttlMinutes = value; }
        public String getReason() { return reason; }
        public void setReason(String value) { reason = value; }
        public String getIdempotencyKey() { return idempotencyKey; }
        public void setIdempotencyKey(String value) { idempotencyKey = value; }
    }
}

package io.github.jasper.monitoring.audit.spring3;

import io.github.jasper.monitoring.api.AuthorizationDecision;
import io.github.jasper.monitoring.api.MonitorAction;
import io.github.jasper.monitoring.api.MonitorActionAttribute;
import io.github.jasper.monitoring.api.MonitorActionAttributeTarget;
import io.github.jasper.monitoring.api.MonitoringContextAccessor;
import io.github.jasper.monitoring.api.ResourceScopeRequest;
import io.github.jasper.monitoring.api.SecurityEventResult;
import io.github.jasper.monitoring.api.SecurityEventType;
import io.github.jasper.monitoring.core.application.ActionEventRecorder;
import io.github.jasper.monitoring.core.application.MonitoringOutcome;
import io.github.jasper.monitoring.core.application.authorization.ResourceAccessGuard;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** 通过 HTTP 触发注册式埋点的最小业务控制器。 */
@RestController
@RequestMapping("/audit")
public class AuditController {
    // This integration fixture must not echo a client-controlled requested row count.
    private static final long SERVER_REPORTED_ROW_COUNT = 37L;
    private final ActionEventRecorder recorder;
    private final ResourceAccessGuard resourceAccessGuard;
    private final MonitoringContextAccessor contextAccessor;
    private final AuditReportCatalog reportCatalog;
    private final AuditExportService exportService;

    /** @param recorder Starter 自动装配的动作记录器 */
    public AuditController(ActionEventRecorder recorder, ResourceAccessGuard resourceAccessGuard,
                           MonitoringContextAccessor contextAccessor,
                           AuditReportCatalog reportCatalog, AuditExportService exportService) {
        this.recorder = recorder;
        this.resourceAccessGuard = resourceAccessGuard;
        this.contextAccessor = contextAccessor;
        this.reportCatalog = reportCatalog;
        this.exportService = exportService;
    }

    @GetMapping("/reports/{reportId}")
    public ResponseEntity<Map<String, Object>> report(@PathVariable("reportId") String reportId) {
        AuditReportCatalog.AuditReport report = reportCatalog.find(reportId);
        if (report == null) {
            return ResponseEntity.notFound().build();
        }
        AuthorizationDecision decision = authorize(report);
        if (!decision.isAllowed()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("reportId", report.getId());
        return ResponseEntity.ok(body);
    }

    @PostMapping("/reports/{reportId}/export")
    public ResponseEntity<Map<String, Object>> exportReport(@PathVariable("reportId") String reportId) {
        AuditReportCatalog.AuditReport report = reportCatalog.find(reportId);
        if (report == null) {
            return ResponseEntity.notFound().build();
        }
        AuthorizationDecision decision = authorize(report);
        if (!decision.isAllowed()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        exportService.export(report);
        return ResponseEntity.ok(exportResponse(SERVER_REPORTED_ROW_COUNT));
    }

    /**
     * 记录一次服务端确认的登录失败。连续调用五次会触发 {@code AUTH-01}。
     *
     * @return 已记录事件标识和本次命中规则数
     */
    @PostMapping("/login-failure")
    public Map<String, Object> loginFailure() {
        MonitoringOutcome outcome = recorder.record("audit:login-failure", contextAccessor.requestContext(),
            contextAccessor.identityContext(),
            SecurityEventResult.FAILURE, "INVALID_PASSWORD");
        return response(outcome);
    }

    /**
     * 记录一笔带服务端动态事实的导出，用于验证注册式埋点、规则标记与导出阈值规则。
     *
     * @return 已记录事件标识和本次命中规则数
     */
    @PostMapping("/export")
    public Map<String, Object> export() {
        MonitoringOutcome outcome = recorder.record(recorder.draft("audit:export", contextAccessor.requestContext(),
            contextAccessor.identityContext()).resourceId("audit-export-2026").dataCount(5000)
            .result(SecurityEventResult.SUCCESS).reasonCode("EXPORT_COMPLETED").build());
        return response(outcome);
    }

    /**
     * 通过 MVC 注解自动记录固定查询动作，用于验证 Web 拦截器路径。
     *
     * @return 简化业务响应；事件由 Starter 请求完成拦截器记录
     */
    @GetMapping("/annotated-query")
    @MonitorAction(value = "audit:annotated-query", resourceType = "audit")
    public Map<String, Object> annotatedQuery() {
        Map<String, Object> response = new LinkedHashMap<String, Object>();
        response.put("status", "ok");
        return response;
    }

    @PostMapping("/annotated-export")
    @MonitorAction(action = "audit:annotated-export", eventType = SecurityEventType.EXPORT,
        resourceType = "report", ruleTags = {"sensitive-data"}, enrichers = AuditExportFacts.class)
    @MonitorActionAttribute(name = "sensitivity", value = "HIGH")
    public Map<String, Object> annotatedExport(
        @MonitorActionAttribute(target = MonitorActionAttributeTarget.RESOURCE_ID, path = "report.id")
        @MonitorActionAttribute(target = MonitorActionAttributeTarget.ORG_SCOPE, path = "tenant.code")
        @RequestBody AuditExportRequest request) {
        return exportResponse(SERVER_REPORTED_ROW_COUNT);
    }

    @PostMapping("/annotated-export-denied")
    @MonitorAction(action = "audit:annotated-export-denied", eventType = SecurityEventType.EXPORT,
        resourceType = "report", ruleTags = {"sensitive-data"}, enrichers = AuditExportFacts.class)
    @MonitorActionAttribute(name = "sensitivity", value = "HIGH")
    public ResponseEntity<Map<String, Object>> annotatedExportDenied(
        @MonitorActionAttribute(target = MonitorActionAttributeTarget.RESOURCE_ID, path = "report.id")
        @MonitorActionAttribute(target = MonitorActionAttributeTarget.ORG_SCOPE, path = "tenant.code")
        @RequestBody AuditExportRequest request) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(exportResponse(SERVER_REPORTED_ROW_COUNT));
    }

    private static Map<String, Object> exportResponse(long rowCount) {
        Map<String, Object> response = new LinkedHashMap<String, Object>();
        response.put("rowCount", Long.valueOf(rowCount));
        return response;
    }

    private static Map<String, Object> response(MonitoringOutcome outcome) {
        Map<String, Object> response = new LinkedHashMap<String, Object>();
        response.put("eventId", outcome.getEvent().getEventId());
        response.put("matchCount", outcome.getMatches().size());
        return response;
    }

    private AuthorizationDecision authorize(AuditReportCatalog.AuditReport report) {
        return resourceAccessGuard.authorize(contextAccessor.identityContext(), new ResourceScopeRequest(
            contextAccessor.requestContext(), "report", report.getId(), report.getOrganization()));
    }
}

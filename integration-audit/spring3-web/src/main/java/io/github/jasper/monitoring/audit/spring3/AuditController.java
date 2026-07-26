package io.github.jasper.monitoring.audit.spring3;

import io.github.jasper.monitoring.api.AuthorizationDecision;
import io.github.jasper.monitoring.api.MonitoringContextAccessor;
import io.github.jasper.monitoring.api.ResourceScopeRequest;
import io.github.jasper.monitoring.api.action.BuiltInActions;
import io.github.jasper.monitoring.api.action.MonitorAction;
import io.github.jasper.monitoring.api.event.ActionExecution;
import io.github.jasper.monitoring.api.event.ActionOutcome;
import io.github.jasper.monitoring.api.fact.ActionFacts;
import io.github.jasper.monitoring.api.fact.BuiltInFacts;
import io.github.jasper.monitoring.api.fact.FactSource;
import io.github.jasper.monitoring.core.application.MonitoringService;
import io.github.jasper.monitoring.core.application.SecurityEventAssembler;
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
    private final MonitoringService monitoring;
    private final ResourceAccessGuard resourceAccessGuard;
    private final MonitoringContextAccessor contextAccessor;
    private final AuditReportCatalog reportCatalog;
    private final AuditExportService exportService;

    /** @param monitoring Starter 自动装配的强类型监测服务 */
    public AuditController(MonitoringService monitoring, ResourceAccessGuard resourceAccessGuard,
                           MonitoringContextAccessor contextAccessor,
                           AuditReportCatalog reportCatalog, AuditExportService exportService) {
        this.monitoring = monitoring;
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
        SecurityEventAssembler.AssemblyResult outcome = monitoring.monitor(ActionExecution.of(
            BuiltInActions.LoginFailure.class, contextAccessor.requestContext(), contextAccessor.identityContext(),
            ActionOutcome.failure("INVALID_PASSWORD", ActionOutcome.ExceptionClassification.AUTHORIZATION, 0L)));
        return response(outcome);
    }

    /**
     * 记录一笔带服务端动态事实的导出，用于验证注册式埋点、规则标记与导出阈值规则。
     *
     * @return 已记录事件标识和本次命中规则数
     */
    @PostMapping("/export")
    public Map<String, Object> export() {
        ActionFacts facts = ActionFacts.builder()
            .put(BuiltInFacts.ResourceId.class, "audit-export-2026")
            .put(BuiltInFacts.DataCount.class, Long.valueOf(5000L)).build();
        SecurityEventAssembler.AssemblyResult outcome = monitoring.monitor(ActionExecution.of(
            BuiltInActions.ReportExport.class, contextAccessor.requestContext(), contextAccessor.identityContext(),
            ActionOutcome.success(0L), facts, FactSource.HOST_PROVIDER));
        return response(outcome);
    }

    /**
     * 通过 MVC 注解自动记录固定查询动作，用于验证 Web 拦截器路径。
     *
     * @return 简化业务响应；事件由 Starter 请求完成拦截器记录
     */
    @GetMapping("/annotated-query")
    @MonitorAction(BuiltInActions.Query.class)
    public Map<String, Object> annotatedQuery() {
        Map<String, Object> response = new LinkedHashMap<String, Object>();
        response.put("status", "ok");
        return response;
    }

    @PostMapping("/annotated-export")
    @MonitorAction(BuiltInActions.Query.class)
    public Map<String, Object> annotatedExport(@RequestBody AuditExportRequest request) {
        return exportResponse(SERVER_REPORTED_ROW_COUNT);
    }

    @PostMapping("/annotated-export-denied")
    public ResponseEntity<Map<String, Object>> annotatedExportDenied(@RequestBody AuditExportRequest request) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(exportResponse(SERVER_REPORTED_ROW_COUNT));
    }

    private static Map<String, Object> exportResponse(long rowCount) {
        Map<String, Object> response = new LinkedHashMap<String, Object>();
        response.put("rowCount", Long.valueOf(rowCount));
        return response;
    }

    private static Map<String, Object> response(SecurityEventAssembler.AssemblyResult outcome) {
        Map<String, Object> response = new LinkedHashMap<String, Object>();
        response.put("eventId", outcome.getEvent().getEventId());
        response.put("action", outcome.getEvent().getAction());
        return response;
    }

    private AuthorizationDecision authorize(AuditReportCatalog.AuditReport report) {
        return resourceAccessGuard.authorize(contextAccessor.identityContext(), new ResourceScopeRequest(
            contextAccessor.requestContext(), "report", report.getId(), report.getOrganization()));
    }
}

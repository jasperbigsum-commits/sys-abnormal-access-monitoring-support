package io.github.jasper.monitoring.audit.spring2.monitoring;

import io.github.jasper.monitoring.api.MonitoringContextAccessor;
import io.github.jasper.monitoring.api.action.BuiltInActions;
import io.github.jasper.monitoring.api.action.MonitorAction;
import io.github.jasper.monitoring.api.event.ActionExecution;
import io.github.jasper.monitoring.api.event.ActionOutcome;
import io.github.jasper.monitoring.api.fact.ActionFacts;
import io.github.jasper.monitoring.api.fact.ActionFact;
import io.github.jasper.monitoring.api.fact.BuiltInFacts;
import io.github.jasper.monitoring.api.fact.FactSource;
import io.github.jasper.monitoring.core.application.MonitoringService;
import io.github.jasper.monitoring.core.application.SecurityEventAssembler;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Monitoring-only endpoints used to verify explicit and annotated instrumentation. */
@RestController
@RequestMapping("/audit")
public class MonitoringFixtureController {
    private static final long SERVER_REPORTED_ROW_COUNT = 37L;
    private final MonitoringService monitoring;
    private final MonitoringContextAccessor contexts;

    public MonitoringFixtureController(MonitoringService monitoring, MonitoringContextAccessor contexts) {
        this.monitoring = monitoring;
        this.contexts = contexts;
    }

    @PostMapping("/login-failure")
    public Map<String, Object> loginFailure() {
        return response(monitoring.monitor(ActionExecution.of(BuiltInActions.LoginFailure.class,
            contexts.requestContext(), contexts.identityContext(), ActionOutcome.failure(
                "INVALID_PASSWORD", ActionOutcome.ExceptionClassification.AUTHORIZATION, 0L))));
    }

    @PostMapping("/export")
    public Map<String, Object> export(@RequestBody AuditExportRequest ignored) {
        ActionFacts facts = ActionFacts.builder()
            .put(BuiltInFacts.ResourceId.class, "audit-export-2026")
            .put(BuiltInFacts.DataCount.class, Long.valueOf(SERVER_REPORTED_ROW_COUNT)).build();
        return response(monitoring.monitor(ActionExecution.of(BuiltInActions.ReportExport.class,
            contexts.requestContext(), contexts.identityContext(), ActionOutcome.success(0L),
            facts, FactSource.HOST_PROVIDER)));
    }

    /**
     * 注解式查询动作采集示例。
     *
     * <p><strong>用例编号</strong>：IA-03。</p>
     * <p><strong>验证核心点</strong>：`@MonitorAction` 在成功路径应产出与动作契约一致的事件类型与结果。</p>
     * <p><strong>注意细节</strong>：仅声明动作不等于声明业务事实；事实仍由显式埋点或事实绑定提供。</p>
     */
    @GetMapping("/annotated-query")
    @MonitorAction(BuiltInActions.Query.class)
    public Map<String, Object> annotatedQuery() {
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("status", "ok");
        return body;
    }

    @GetMapping("/context-only")
    public Map<String, Object> contextOnly() {
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("requestId", contexts.requestContext().getRequestId());
        return body;
    }

    /**
     * 注解动作 + 嵌套参数事实绑定示例。
     *
     * <p><strong>用例编号</strong>：IA-04。</p>
     * <p><strong>验证核心点</strong>：`@ActionFact(path = "report.rows")` 能将嵌套入参映射为强类型 Fact，
     * 并在入库事实中保留 METHOD_PARAMETER 来源。</p>
     * <p><strong>注意细节</strong>：路径解析失败或类型不匹配应在采集阶段显式失败，避免脏事实入库。</p>
     */
    @PostMapping("/annotated-export")
    @MonitorAction(BuiltInActions.SensitiveView.class)
    public Map<String, Object> annotatedExport(
            @RequestBody @ActionFact(value = BuiltInFacts.DataCount.class, path = "report.rows")
            AuditExportRequest ignored) {
        return exportResponse();
    }

    /**
     * 注解式拒绝结果分类示例。
     *
     * <p><strong>用例编号</strong>：IA-03。</p>
     * <p><strong>验证核心点</strong>：当控制器返回 403 时，注解动作应将结果分类为 DENIED，而非 SUCCESS。</p>
     * <p><strong>注意细节</strong>：拒绝分类依赖响应语义，不应由客户端自报字段决定。</p>
     */
    @PostMapping("/annotated-export-denied")
    @MonitorAction(BuiltInActions.Query.class)
    public ResponseEntity<Map<String, Object>> annotatedExportDenied(@RequestBody AuditExportRequest ignored) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(exportResponse());
    }

    private static Map<String, Object> exportResponse() {
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("rowCount", Long.valueOf(SERVER_REPORTED_ROW_COUNT));
        return body;
    }

    private static Map<String, Object> response(SecurityEventAssembler.AssemblyResult outcome) {
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("eventId", outcome.getEvent().getEventId());
        body.put("action", outcome.getEvent().getAction());
        return body;
    }
}

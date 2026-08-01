package io.github.jasper.monitoring.audit.spring2.monitoring;

import io.github.jasper.monitoring.api.MonitoringContextAccessor;
import io.github.jasper.monitoring.api.action.BuiltInActions;
import io.github.jasper.monitoring.api.code.BuiltInReasonCodes;
import io.github.jasper.monitoring.api.authentication.AuthenticationMonitor;
import io.github.jasper.monitoring.api.authentication.AuthenticationStage;
import io.github.jasper.monitoring.api.authentication.LoginSubjectInput;
import io.github.jasper.monitoring.api.action.MonitorAction;
import io.github.jasper.monitoring.api.event.ActionOutcome;
import io.github.jasper.monitoring.api.fact.ActionFacts;
import io.github.jasper.monitoring.api.fact.BuiltInFacts;
import io.github.jasper.monitoring.core.application.SecurityEventAssembler;
import io.github.jasper.monitoring.spring.support.MonitoringRecorder;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用于验证显式埋点、注解监听和上下文边界的 Spring2 验收 Controller。
 *
 * <p>本类是 Spring3 监测示例的 Boot 2/ {@code javax.servlet} 对照实现；请求上下文不会自动产生
 * 业务事件，真实业务仍应在 Service 决策点提交可信 Action 和 Fact。</p>
 */
@RestController
@RequestMapping("/audit")
public class MonitoringFixtureController {
    private static final long SERVER_REPORTED_ROW_COUNT = 37L;
    private final MonitoringRecorder monitoringRecorder;
    private final MonitoringContextAccessor contexts;
    private final AnnotatedMonitoringService annotatedMonitoring;
    private final AuthenticationMonitor authenticationMonitor;

    public MonitoringFixtureController(MonitoringRecorder monitoringRecorder, MonitoringContextAccessor contexts,
            AnnotatedMonitoringService annotatedMonitoring, AuthenticationMonitor authenticationMonitor) {
        this.monitoringRecorder = monitoringRecorder;
        this.contexts = contexts;
        this.annotatedMonitoring = annotatedMonitoring;
        this.authenticationMonitor = authenticationMonitor;
    }

    @PostMapping("/login-failure")
    public Map<String, Object> loginFailure(@RequestHeader("X-Audit-Principal") String loginUser) {
        authenticationMonitor.recordDenied(new LoginSubjectInput(loginUser, "audit"),
            AuthenticationStage.CREDENTIAL, BuiltInReasonCodes.Authentication.INVALID_CREDENTIAL);
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("action", "auth:login");
        result.put("status", "recorded");
        return result;
    }

    @PostMapping("/export")
    public Map<String, Object> export(@RequestBody AuditExportRequest ignored) {
        ActionFacts facts = ActionFacts.builder()
            .put(BuiltInFacts.ResourceId.class, "audit-export-2026")
            .put(BuiltInFacts.DataCount.class, Long.valueOf(SERVER_REPORTED_ROW_COUNT)).build();
        return response(monitoringRecorder.record(BuiltInActions.ReportExport.class,
            ActionOutcome.success(0L), facts));
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
     * 普通 Service 注解动作 + 运行时事实示例。
     *
     * <p><strong>用例编号</strong>：IA-04。</p>
     * <p><strong>验证核心点</strong>：普通 Service 方法内可追加执行后得到的 Fact，
     * 并在入库事实中保留 HOST_PROVIDER 来源。</p>
     * <p><strong>注意细节</strong>：客户端上报的行数不会覆盖服务端计算结果。</p>
     */
    @PostMapping("/annotated-export")
    public Map<String, Object> annotatedExport(@RequestBody AuditExportRequest ignored) {
        return exportResponse(annotatedMonitoring.export(ignored));
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
        return exportResponse(SERVER_REPORTED_ROW_COUNT);
    }

    private static Map<String, Object> exportResponse(long rowCount) {
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("rowCount", Long.valueOf(rowCount));
        return body;
    }

    private static Map<String, Object> response(SecurityEventAssembler.AssemblyResult outcome) {
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("eventId", outcome.getEvent().getEventId());
        body.put("action", outcome.getEvent().getAction());
        return body;
    }
}

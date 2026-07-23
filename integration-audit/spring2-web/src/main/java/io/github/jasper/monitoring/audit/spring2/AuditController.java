package io.github.jasper.monitoring.audit.spring2;

import io.github.jasper.monitoring.api.IdentityContext;
import io.github.jasper.monitoring.api.MonitorAction;
import io.github.jasper.monitoring.api.MonitoringRequestContext;
import io.github.jasper.monitoring.api.SecurityEventResult;
import io.github.jasper.monitoring.core.application.ActionEventRecorder;
import io.github.jasper.monitoring.core.application.MonitoringOutcome;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 通过 HTTP 触发注册式埋点的最小业务控制器。 */
@RestController
@RequestMapping("/audit")
public class AuditController {
    private static final String REQUEST_CONTEXT_ATTRIBUTE = "io.github.jasper.monitoring.request-context";
    private static final String IDENTITY_CONTEXT_ATTRIBUTE = "io.github.jasper.monitoring.identity-context";
    private final ActionEventRecorder recorder;

    /** @param recorder Starter 自动装配的动作记录器 */
    public AuditController(ActionEventRecorder recorder) {
        this.recorder = recorder;
    }

    /**
     * 记录一次服务端确认的登录失败。连续调用五次会触发 {@code AUTH-01}。
     *
     * @param request 当前 Servlet 请求；上下文由 Starter 拦截器建立
     * @return 已记录事件标识和本次命中规则数
     */
    @PostMapping("/login-failure")
    public Map<String, Object> loginFailure(HttpServletRequest request) {
        MonitoringOutcome outcome = recorder.record("audit:login-failure", requestContext(request), identityContext(request),
            SecurityEventResult.FAILURE, "INVALID_PASSWORD");
        return response(outcome);
    }

    /**
     * 记录一笔带服务端动态事实的导出，用于验证注册式埋点、规则标记与导出阈值规则。
     *
     * @param request 当前 Servlet 请求；上下文由 Starter 拦截器建立
     * @return 已记录事件标识和本次命中规则数
     */
    @PostMapping("/export")
    public Map<String, Object> export(HttpServletRequest request) {
        MonitoringOutcome outcome = recorder.record(recorder.draft("audit:export", requestContext(request),
            identityContext(request)).resourceId("audit-export-2026").dataCount(5000)
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

    private static Map<String, Object> response(MonitoringOutcome outcome) {
        Map<String, Object> response = new LinkedHashMap<String, Object>();
        response.put("eventId", outcome.getEvent().getEventId());
        response.put("matchCount", outcome.getMatches().size());
        return response;
    }

    private static MonitoringRequestContext requestContext(HttpServletRequest request) {
        Object value = request.getAttribute(REQUEST_CONTEXT_ATTRIBUTE);
        if (!(value instanceof MonitoringRequestContext)) {
            throw new IllegalStateException("Trusted monitoring request context is unavailable");
        }
        return (MonitoringRequestContext) value;
    }

    private static IdentityContext identityContext(HttpServletRequest request) {
        Object value = request.getAttribute(IDENTITY_CONTEXT_ATTRIBUTE);
        if (!(value instanceof IdentityContext)) {
            throw new IllegalStateException("Trusted monitoring identity context is unavailable");
        }
        return (IdentityContext) value;
    }
}

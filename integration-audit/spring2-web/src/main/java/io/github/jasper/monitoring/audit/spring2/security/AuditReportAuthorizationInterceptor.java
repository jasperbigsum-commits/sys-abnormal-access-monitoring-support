package io.github.jasper.monitoring.audit.spring2.security;

import io.github.jasper.monitoring.api.AuthorizationDecision;
import io.github.jasper.monitoring.api.MonitoringContextAccessor;
import io.github.jasper.monitoring.api.ResourceScopeRequest;
import io.github.jasper.monitoring.audit.spring2.report.AuditReportCatalog;
import io.github.jasper.monitoring.core.application.authorization.ResourceAccessGuard;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;
import java.util.Map;

/**
 * 报告 HTTP 资源的唯一 fail-closed 授权边界。
 *
 * <p>拦截器的先授权后访问模式可供生产参考；报告目录、夹具 Realm 和固定响应策略属于集成夹具实现。</p>
 */
public final class AuditReportAuthorizationInterceptor implements HandlerInterceptor {
    // 集成实现：在验收请求内传递已授权的夹具报告，避免 Controller 再次按客户端标识查询。
    public static final String AUTHORIZED_REPORT =
        AuditReportAuthorizationInterceptor.class.getName() + ".authorizedReport";

    private final ResourceAccessGuard guard;
    private final MonitoringContextAccessor contexts;
    private final AuditReportCatalog reports;
    private final AuditRbacRealm realm;

    public AuditReportAuthorizationInterceptor(ResourceAccessGuard guard,
                                               MonitoringContextAccessor contexts,
                                               AuditReportCatalog reports, AuditRbacRealm realm) {
        this.guard = guard;
        this.contexts = contexts;
        this.reports = reports;
        this.realm = realm;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 集成夹具实现：从固定报告目录解析资源，而非从生产业务仓储读取。
        String reportId = pathVariable(request, "reportId");
        AuditReportCatalog.AuditReport report = reports.find(reportId);
        if (report == null) {
            response.setStatus(HttpStatus.NOT_FOUND.value());
            return false;
        }
        // 集成实现：跨组织统一返回 404，以验证资源存在性不泄露。
        boolean hiddenByScope = !report.getOrganization().equals(organizationOfCurrentActor());
        AuthorizationDecision decision = guard.authorize(contexts.identityContext(), new ResourceScopeRequest(
            contexts.requestContext(), "report", report.getId(), report.getOrganization()));
        if (hiddenByScope || decision == null || !decision.isAllowed()) {
            response.setStatus(hiddenByScope ? HttpStatus.NOT_FOUND.value() : HttpStatus.FORBIDDEN.value());
            return false;
        }
        request.setAttribute(AUTHORIZED_REPORT, report);
        return true;
    }

    private String organizationOfCurrentActor() {
        return realm.organization(contexts.identityContext().getUserId());
    }

    @SuppressWarnings("unchecked")
    private static String pathVariable(HttpServletRequest request, String name) {
        Object value = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        return value instanceof Map ? ((Map<String, String>) value).get(name) : null;
    }
}

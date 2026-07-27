package io.github.jasper.monitoring.audit.spring2.security;

import io.github.jasper.monitoring.api.AuthorizationDecision;
import io.github.jasper.monitoring.api.MonitoringContextAccessor;
import io.github.jasper.monitoring.api.ResourceScopeRequest;
import io.github.jasper.monitoring.audit.spring2.report.AuditReportCatalog;
import io.github.jasper.monitoring.audit.spring2.AuditRbacRealm;
import io.github.jasper.monitoring.core.application.authorization.ResourceAccessGuard;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;
import java.util.Map;

/** The single fail-closed authorization boundary for report HTTP resources. */
public final class AuditReportAuthorizationInterceptor implements HandlerInterceptor {
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
        String reportId = pathVariable(request, "reportId");
        AuditReportCatalog.AuditReport report = reports.find(reportId);
        if (report == null) {
            response.setStatus(HttpStatus.NOT_FOUND.value());
            return false;
        }
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

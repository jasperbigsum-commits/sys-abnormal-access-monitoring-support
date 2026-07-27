package io.github.jasper.monitoring.audit.spring2.security;

import io.github.jasper.monitoring.api.MonitoringContextAccessor;
import io.github.jasper.monitoring.audit.spring2.report.AuditReportCatalog;
import io.github.jasper.monitoring.core.application.authorization.ResourceAccessGuard;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Installs host authorization before report controllers execute. */
@Configuration
public class AuditWebSecurityConfiguration implements WebMvcConfigurer {
    private final AuditReportAuthorizationInterceptor reportAuthorization;

    public AuditWebSecurityConfiguration(ResourceAccessGuard guard, MonitoringContextAccessor contexts,
                                         AuditReportCatalog reports) {
        this.reportAuthorization = new AuditReportAuthorizationInterceptor(guard, contexts, reports);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(reportAuthorization).addPathPatterns("/audit/reports/**").order(100);
    }
}

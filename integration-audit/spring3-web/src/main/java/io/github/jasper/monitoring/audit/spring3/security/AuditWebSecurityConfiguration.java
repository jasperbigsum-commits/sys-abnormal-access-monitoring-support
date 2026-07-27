package io.github.jasper.monitoring.audit.spring3.security;

import io.github.jasper.monitoring.api.MonitoringContextAccessor;
import io.github.jasper.monitoring.audit.spring3.report.AuditReportCatalog;
import io.github.jasper.monitoring.core.application.authorization.ResourceAccessGuard;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Installs host authorization before report controllers execute. */
@Configuration
public class AuditWebSecurityConfiguration implements WebMvcConfigurer {
    private final AuditReportAuthorizationInterceptor reportAuthorization;

    public AuditWebSecurityConfiguration(ResourceAccessGuard guard, MonitoringContextAccessor contexts,
                                         AuditReportCatalog reports, org.apache.shiro.realm.Realm realm) {
        this.reportAuthorization = new AuditReportAuthorizationInterceptor(guard, contexts, reports,
            (AuditRbacRealm) realm);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(reportAuthorization).addPathPatterns("/audit/reports/**").order(100);
    }
}

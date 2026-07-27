package io.github.jasper.monitoring.audit.spring2.security;

import io.github.jasper.monitoring.api.AccountType;
import io.github.jasper.monitoring.api.AuthorizationDecision;
import io.github.jasper.monitoring.api.IdentityContext;
import io.github.jasper.monitoring.api.IdentityContextProvider;
import io.github.jasper.monitoring.api.ResourceScopeAuthorizer;
import io.github.jasper.monitoring.audit.spring2.report.AuditReportCatalog;
import io.github.jasper.monitoring.audit.spring2.persistence.AuditFixtureRepository;
import java.util.Collections;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.realm.Realm;
import org.apache.shiro.spring.web.config.DefaultShiroFilterChainDefinition;
import org.apache.shiro.spring.web.config.ShiroFilterChainDefinition;
import org.apache.shiro.subject.Subject;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Shiro configuration isolated to the Boot 2 acceptance fixture. */
@Configuration
public class AuditShiroRbacConfiguration {
    @Bean
    public Realm auditRbacRealm(AuditFixtureRepository fixtures) {
        return new AuditRbacRealm(fixtures);
    }

    @Bean
    public AuditPrincipalFilter auditPrincipalFilter(Realm realm) {
        return new AuditPrincipalFilter((AuditRbacRealm) realm);
    }

    @Bean
    public FilterRegistrationBean<AuditPrincipalFilter> auditPrincipalFilterRegistration(
        AuditPrincipalFilter filter) {
        FilterRegistrationBean<AuditPrincipalFilter> registration =
            new FilterRegistrationBean<AuditPrincipalFilter>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public ShiroFilterChainDefinition shiroFilterChainDefinition() {
        DefaultShiroFilterChainDefinition chain = new DefaultShiroFilterChainDefinition();
        chain.addPathDefinition("/audit/authentication/**", "anon");
        chain.addPathDefinition("/audit/**", "auditPrincipalFilter");
        return chain;
    }

    @Bean
    public IdentityContextProvider auditIdentityContextProvider() {
        return request -> authenticatedIdentity();
    }

    @Bean
    public ResourceScopeAuthorizer auditResourceScopeAuthorizer(AuditReportCatalog catalog, Realm realm) {
        return (identity, request) -> {
            Subject subject = SecurityUtils.getSubject();
            String principal = subject.getPrincipal() == null ? null : String.valueOf(subject.getPrincipal());
            AuditReportCatalog.AuditReport report = catalog.find(request.getResourceId());
            String permission = "POST".equals(request.getRequest().getMethod())
                ? "report:export" : "report:read";
            boolean allowed = subject.isAuthenticated()
                && principal != null
                && identity != null
                && principal.equals(identity.getUserId())
                && report != null
                && report.getOrganization().equals(request.getOrgScope())
                && report.getOrganization().equals(((AuditRbacRealm) realm).organization(principal))
                && subject.isPermitted(permission);
            return allowed ? AuthorizationDecision.allowed()
                : AuthorizationDecision.denied("RESOURCE_SCOPE_DENIED");
        };
    }

    private static IdentityContext authenticatedIdentity() {
        Subject subject = SecurityUtils.getSubject();
        if (!subject.isAuthenticated() || subject.getPrincipal() == null) {
            return IdentityContext.anonymous();
        }
        String principal = String.valueOf(subject.getPrincipal());
        return new IdentityContext(principal, AccountType.PERSON,
            Collections.singleton(principal), null);
    }
}

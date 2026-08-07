package io.github.jasper.monitoring.audit.spring2.security;

import io.github.jasper.monitoring.api.AccountType;
import io.github.jasper.monitoring.api.AuthorizationDecision;
import io.github.jasper.monitoring.api.IdentityContext;
import io.github.jasper.monitoring.api.IdentityContextProvider;
import io.github.jasper.monitoring.api.ResourceScopeAuthorizer;
import io.github.jasper.monitoring.api.ResourceScopeResolution;
import io.github.jasper.monitoring.api.ResourceScopeResolver;
import io.github.jasper.monitoring.api.fact.ActionFacts;
import io.github.jasper.monitoring.api.fact.BuiltInFacts;
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

/**
 * 隔离在 Boot 2 验收宿主中的 Shiro 配置。
 *
 * <p>本配置包含多个集成夹具 Bean；生产应保留可信身份派生与资源范围授权边界，
 * 但必须替换固定 Realm、测试过滤器、URL 链和夹具系统范围。</p>
 */
@Configuration
public class AuditShiroRbacConfiguration {
    @Bean
    public Realm auditRbacRealm(AuditFixtureRepository fixtures) {
        // 集成夹具实现：使用 audit_* 测试表提供 Shiro 账号和角色。
        return new AuditRbacRealm(fixtures);
    }

    @Bean
    public AuditPrincipalFilter auditPrincipalFilter(Realm realm) {
        // 集成夹具实现：将 X-Audit-Principal 转换为测试 Subject。
        return new AuditPrincipalFilter((AuditRbacRealm) realm);
    }

    @Bean
    public FilterRegistrationBean<AuditPrincipalFilter> auditPrincipalFilterRegistration(
        AuditPrincipalFilter filter) {
        // 集成夹具实现：禁止 Spring 直接注册，确保过滤器只通过 Shiro 链执行一次。
        FilterRegistrationBean<AuditPrincipalFilter> registration =
            new FilterRegistrationBean<AuditPrincipalFilter>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public ShiroFilterChainDefinition shiroFilterChainDefinition() {
        // 集成夹具实现：匿名认证端点与固定 /audit/** 保护路径仅服务验收路由。
        DefaultShiroFilterChainDefinition chain = new DefaultShiroFilterChainDefinition();
        chain.addPathDefinition("/audit/authentication/**", "anon");
        chain.addPathDefinition("/audit/**", "auditPrincipalFilter");
        return chain;
    }

    @Bean
    public IdentityContextProvider auditIdentityContextProvider() {
        // 集成实现：从已认证 Subject 派生监测身份；生产可替换为 SSO/JWT/会话适配器。
        return request -> authenticatedIdentity();
    }

    @Bean
    public ResourceScopeResolver auditResourceScopeResolver(AuditReportCatalog catalog) {
        return request -> {
            AuditReportCatalog.AuditReport report = catalog.find(request.getResourceId());
            return report == null ? ResourceScopeResolution.unresolved()
                : ResourceScopeResolution.resolved(ActionFacts.builder()
                    .put(BuiltInFacts.OrgScope.class, report.getOrganization()).build());
        };
    }

    @Bean
    public ResourceScopeAuthorizer auditResourceScopeAuthorizer(Realm realm) {
        return (identity, request) -> {
            // 资源目录已由 resolver 查询一次；授权器消费同一可信组织范围。
            Subject subject = SecurityUtils.getSubject();
            String principal = subject.getPrincipal() == null ? null : String.valueOf(subject.getPrincipal());
            String permission = "POST".equals(request.getRequest().getMethod())
                ? "report:export" : "report:read";
            boolean allowed = subject.isAuthenticated()
                && principal != null
                && identity != null
                && principal.equals(identity.getUserId())
                && request.getOrgScope() != null
                && request.getOrgScope().equals(((AuditRbacRealm) realm).organization(principal))
                && subject.isPermitted(permission);
            return allowed ? AuthorizationDecision.allowed()
                : AuthorizationDecision.denied(
                    io.github.jasper.monitoring.api.code.BuiltInReasonCodes.Authorization.RESOURCE_SCOPE_DENIED);
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

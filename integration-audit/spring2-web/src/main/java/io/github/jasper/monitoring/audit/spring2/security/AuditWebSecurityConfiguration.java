package io.github.jasper.monitoring.audit.spring2.security;

import io.github.jasper.monitoring.api.MonitoringContextAccessor;
import io.github.jasper.monitoring.audit.spring2.report.AuditReportCatalog;
import io.github.jasper.monitoring.core.application.authorization.ResourceAccessGuard;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 在报告 Controller 前安装宿主资源授权拦截器。
 *
 * <p>拦截器安装方式可供生产参考；具体报告目录和 Realm 强制转换是集成夹具实现。</p>
 */
@Configuration
public class AuditWebSecurityConfiguration implements WebMvcConfigurer {
    private final AuditReportAuthorizationInterceptor reportAuthorization;

    public AuditWebSecurityConfiguration(ResourceAccessGuard guard, MonitoringContextAccessor contexts,
                                         AuditReportCatalog reports, org.apache.shiro.realm.Realm realm) {
        // 集成夹具实现：使用固定报告目录和夹具 Realm 创建授权边界。
        this.reportAuthorization = new AuditReportAuthorizationInterceptor(guard, contexts, reports,
            (AuditRbacRealm) realm);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 集成夹具实现：仅保护验收报告路由；生产应覆盖全部真实资源路由。
        registry.addInterceptor(reportAuthorization).addPathPatterns("/audit/reports/**").order(100);
    }
}

package io.github.jasper.monitoring.audit.spring2.security;

import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.shiro.authc.AuthenticationException;
import org.apache.shiro.authc.UsernamePasswordToken;
import org.apache.shiro.subject.Subject;
import org.apache.shiro.web.filter.AccessControlFilter;
import io.github.jasper.monitoring.audit.spring2.persistence.AuditFixtureRepository;

/**
 * 将固定夹具请求头转换为已认证 Shiro Subject 的过滤器。
 *
 * <p>这是集成夹具实现，不能作为生产认证机制使用。</p>
 */
public final class AuditPrincipalFilter extends AccessControlFilter {
    // 集成夹具实现：仅供验收请求指定固定测试身份。
    static final String HEADER_NAME = "X-Audit-Principal";
    private final AuditRbacRealm realm;

    public AuditPrincipalFilter(AuditRbacRealm realm) { this.realm = realm; }

    @Override
    protected boolean isAccessAllowed(ServletRequest request, ServletResponse response, Object mappedValue) {
        // 集成夹具实现：从测试 Header 读取主体；生产应由可信认证过滤器建立 Subject。
        String principal = ((HttpServletRequest) request).getHeader(HEADER_NAME);
        if (!realm.supportsPrincipal(principal)) {
            return false;
        }
        Subject subject = getSubject(request, response);
        if (subject.isAuthenticated() && principal.equals(String.valueOf(subject.getPrincipal()))) {
            return true;
        }
        if (subject.getPrincipal() != null) {
            subject.logout();
        }
        try {
            // 集成夹具实现：使用固定夹具凭据登录，不接受真实密码。
            subject.login(new UsernamePasswordToken(principal, AuditRbacRealm.FIXTURE_CREDENTIAL));
            return true;
        } catch (AuthenticationException ignored) {
            return false;
        }
    }

    @Override
    protected boolean onAccessDenied(ServletRequest request, ServletResponse response) {
        ((HttpServletResponse) response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        return false;
    }
}

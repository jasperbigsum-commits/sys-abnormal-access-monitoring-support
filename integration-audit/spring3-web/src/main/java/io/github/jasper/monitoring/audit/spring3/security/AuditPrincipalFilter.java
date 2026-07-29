package io.github.jasper.monitoring.audit.spring3.security;

import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.shiro.authc.AuthenticationException;
import org.apache.shiro.authc.UsernamePasswordToken;
import org.apache.shiro.subject.Subject;
import org.apache.shiro.web.filter.AccessControlFilter;
import io.github.jasper.monitoring.audit.spring3.persistence.AuditFixtureRepository;

/**
 * 将固定夹具请求头转换为已认证 Shiro Subject 的过滤器。
 *
 * <p>它只读取验收请求的固定 Header，并使用 Realm 的固定凭据建立 Subject。</p>
 */
public final class AuditPrincipalFilter extends AccessControlFilter {
    // 集成夹具实现：仅供验收请求指定固定测试身份。
    static final String HEADER_NAME = "X-Audit-Principal";
    private final AuditRbacRealm realm;

    public AuditPrincipalFilter(AuditRbacRealm realm) { this.realm = realm; }

    @Override
    protected boolean isAccessAllowed(ServletRequest request, ServletResponse response, Object mappedValue) {
        // 从验收 Header 读取主体；Subject 的账号状态仍由 Realm 查询夹具表确认。
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

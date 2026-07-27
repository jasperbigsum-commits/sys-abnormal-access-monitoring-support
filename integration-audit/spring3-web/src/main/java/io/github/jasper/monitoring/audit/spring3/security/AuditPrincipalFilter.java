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

/** Converts a fixed fixture header into an authenticated Shiro subject. */
public final class AuditPrincipalFilter extends AccessControlFilter {
    static final String HEADER_NAME = "X-Audit-Principal";
    private final AuditRbacRealm realm;

    public AuditPrincipalFilter(AuditRbacRealm realm) { this.realm = realm; }

    @Override
    protected boolean isAccessAllowed(ServletRequest request, ServletResponse response, Object mappedValue) {
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

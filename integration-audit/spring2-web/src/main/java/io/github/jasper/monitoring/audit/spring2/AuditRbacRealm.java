package io.github.jasper.monitoring.audit.spring2;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.apache.shiro.authc.AuthenticationException;
import org.apache.shiro.authc.AuthenticationInfo;
import org.apache.shiro.authc.AuthenticationToken;
import org.apache.shiro.authc.SimpleAuthenticationInfo;
import org.apache.shiro.authc.UnknownAccountException;
import org.apache.shiro.authz.AuthorizationInfo;
import org.apache.shiro.authz.SimpleAuthorizationInfo;
import org.apache.shiro.realm.AuthorizingRealm;
import org.apache.shiro.subject.PrincipalCollection;

/** In-memory identities used only by this integration audit fixture. */
public final class AuditRbacRealm extends AuthorizingRealm {
    static final String FIXTURE_CREDENTIAL = "audit-fixture";
    private static final Set<String> PRINCIPALS = new HashSet<String>(
        Arrays.asList("audit-viewer", "audit-exporter"));

    static boolean supportsPrincipal(String principal) {
        return PRINCIPALS.contains(principal);
    }

    static String organization(String principal) {
        return supportsPrincipal(principal) ? "org-a" : null;
    }

    @Override
    protected AuthenticationInfo doGetAuthenticationInfo(AuthenticationToken token) throws AuthenticationException {
        String principal = String.valueOf(token.getPrincipal());
        if (!supportsPrincipal(principal)) {
            throw new UnknownAccountException("Unknown audit fixture principal");
        }
        return new SimpleAuthenticationInfo(principal, FIXTURE_CREDENTIAL, getName());
    }

    @Override
    protected AuthorizationInfo doGetAuthorizationInfo(PrincipalCollection principals) {
        String principal = String.valueOf(principals.getPrimaryPrincipal());
        SimpleAuthorizationInfo info = new SimpleAuthorizationInfo();
        if ("audit-exporter".equals(principal)) {
            info.addRole("audit-exporter");
            info.addStringPermission("report:read");
            info.addStringPermission("report:export");
        } else if ("audit-viewer".equals(principal)) {
            info.addRole("audit-viewer");
            info.addStringPermission("report:read");
        }
        return info;
    }
}

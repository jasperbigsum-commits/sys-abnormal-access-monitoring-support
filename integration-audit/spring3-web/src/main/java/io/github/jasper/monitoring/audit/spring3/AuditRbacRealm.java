package io.github.jasper.monitoring.audit.spring3;

import io.github.jasper.monitoring.audit.spring3.persistence.AuditFixtureRepository;
import java.util.List;
import java.util.Map;
import org.apache.shiro.authc.AuthenticationException;
import org.apache.shiro.authc.AuthenticationInfo;
import org.apache.shiro.authc.AuthenticationToken;
import org.apache.shiro.authc.SimpleAuthenticationInfo;
import org.apache.shiro.authc.UnknownAccountException;
import org.apache.shiro.authz.AuthorizationInfo;
import org.apache.shiro.authz.SimpleAuthorizationInfo;
import org.apache.shiro.realm.AuthorizingRealm;
import org.apache.shiro.subject.PrincipalCollection;

/** Shiro adapter over the reference host's MyBatis account and role state. */
public final class AuditRbacRealm extends AuthorizingRealm {
    static final String FIXTURE_CREDENTIAL = "audit-fixture";
    private final AuditFixtureRepository fixtures;

    public AuditRbacRealm(AuditFixtureRepository fixtures) { this.fixtures = fixtures; }

    public boolean supportsPrincipal(String principal) {
        Map<String, Object> account = fixtures.findAccount(principal);
        return !account.isEmpty() && "ACTIVE".equals(String.valueOf(account.get("STATUS")));
    }

    public String organization(String principal) {
        Map<String, Object> account = fixtures.findAccount(principal);
        return account.isEmpty() ? null : String.valueOf(account.get("ORGANIZATIONID"));
    }

    @Override
    protected AuthenticationInfo doGetAuthenticationInfo(AuthenticationToken token) throws AuthenticationException {
        String principal = String.valueOf(token.getPrincipal());
        if (!supportsPrincipal(principal)) throw new UnknownAccountException("Unknown or disabled audit fixture principal");
        return new SimpleAuthenticationInfo(principal, FIXTURE_CREDENTIAL, getName());
    }

    @Override
    protected AuthorizationInfo doGetAuthorizationInfo(PrincipalCollection principals) {
        String principal = String.valueOf(principals.getPrimaryPrincipal());
        SimpleAuthorizationInfo info = new SimpleAuthorizationInfo();
        List<String> roles = fixtures.findRoles(principal);
        info.addRoles(roles);
        if (roles.contains("audit-exporter")) { info.addStringPermission("report:read"); info.addStringPermission("report:export"); }
        if (roles.contains("audit-viewer")) info.addStringPermission("report:read");
        if (roles.contains("audit-admin")) info.addStringPermission("monitoring:manage");
        if (roles.contains("audit-query")) info.addStringPermission("report:read");
        return info;
    }
}

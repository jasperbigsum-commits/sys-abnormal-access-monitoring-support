package io.github.jasper.monitoring.audit.spring2.security;

import io.github.jasper.monitoring.audit.spring2.persistence.AuditFixtureRepository;
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

/**
 * 基于参考宿主 MyBatis 账号与角色状态的 Shiro Realm。
 *
 * <p>这是集成夹具实现：固定凭据和角色到权限的映射只服务验收场景，生产应接入真实身份目录与授权策略。</p>
 */
public final class AuditRbacRealm extends AuthorizingRealm {
    // 集成夹具实现：所有测试主体共用的固定凭据。
    static final String FIXTURE_CREDENTIAL = "audit-fixture";
    private final AuditFixtureRepository fixtures;

    public AuditRbacRealm(AuditFixtureRepository fixtures) { this.fixtures = fixtures; }

    public boolean supportsPrincipal(String principal) {
        // 集成夹具实现：从 audit_account 读取固定测试账号状态。
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
        // 集成夹具实现：将夹具角色映射为验收接口所需的最小权限集合。
        if (roles.contains("audit-exporter")) { info.addStringPermission("report:read"); info.addStringPermission("report:export"); }
        if (roles.contains("audit-viewer")) info.addStringPermission("report:read");
        if (roles.contains("audit-admin")) info.addStringPermission("monitoring:manage");
        if (roles.contains("audit-query")) info.addStringPermission("report:read");
        return info;
    }
}

package io.github.jasper.monitoring.api;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 仅由宿主后端解析的已认证身份。
 *
 * <p>构造时会复制并清洗值，避免集成方随后修改监测和授权所使用的角色集合。</p>
 */
public final class IdentityContext {
    private final String userId;
    private final AccountType accountType;
    private final Set<String> roleIds;
    private final String sessionIdHash;
    /**
     * 创建不可变的身份快照。
     *
     * @param userId 已认证时由宿主系统提供的用户标识
     * @param accountType 主体类型；缺省时为 {@link AccountType#ANONYMOUS}
     * @param roleIds 宿主角色标识，可为空集合
     * @param sessionIdHash 单向散列后的会话标识，绝不能传入原始会话令牌
     */
    public IdentityContext(String userId, AccountType accountType, Set<String> roleIds, String sessionIdHash) {
        this.userId = SecurityFieldSanitizer.text(userId, 128);
        this.accountType = accountType == null ? AccountType.ANONYMOUS : accountType;
        this.roleIds = Collections.unmodifiableSet(new LinkedHashSet<String>(roleIds == null ? Collections.<String>emptySet() : roleIds));
        this.sessionIdHash = SecurityFieldSanitizer.text(sessionIdHash, 256);
    }
    /**
     * 创建用于未认证请求的标准身份。
     *
     * @return 不含角色和会话标识的不可变匿名身份
     */
    public static IdentityContext anonymous() { return new IdentityContext(null, AccountType.ANONYMOUS, Collections.<String>emptySet(), null); }

    /** @return 已清洗的宿主用户标识；未认证时为 {@code null} */
    public String getUserId() { return userId; }

    /** @return 已认证主体的类型 */
    public AccountType getAccountType() { return accountType; }

    /** @return 不可变的宿主角色标识集合 */
    public Set<String> getRoleIds() { return roleIds; }

    /** @return 单向散列后的会话标识；不可用时为 {@code null} */
    public String getSessionIdHash() { return sessionIdHash; }
}

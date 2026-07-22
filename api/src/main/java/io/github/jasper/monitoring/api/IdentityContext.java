package io.github.jasper.monitoring.api;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Authenticated identity resolved only by the host backend.
 *
 * <p>Values are copied and sanitized at construction time so integrations cannot subsequently
 * mutate the role set used by monitoring and authorization.</p>
 */
public final class IdentityContext {
    private final String userId;
    private final AccountType accountType;
    private final Set<String> roleIds;
    private final String sessionIdHash;
    /**
     * Creates an immutable identity snapshot.
     *
     * @param userId host user identifier, when authenticated
     * @param accountType principal class; defaults to {@link AccountType#ANONYMOUS}
     * @param roleIds host role identifiers; may be empty
     * @param sessionIdHash one-way session identifier, never a raw session token
     */
    public IdentityContext(String userId, AccountType accountType, Set<String> roleIds, String sessionIdHash) {
        this.userId = SecurityFieldSanitizer.text(userId, 128);
        this.accountType = accountType == null ? AccountType.ANONYMOUS : accountType;
        this.roleIds = Collections.unmodifiableSet(new LinkedHashSet<String>(roleIds == null ? Collections.<String>emptySet() : roleIds));
        this.sessionIdHash = SecurityFieldSanitizer.text(sessionIdHash, 256);
    }
    /**
     * Creates the standard identity for an unauthenticated request.
     *
     * @return immutable anonymous identity with no roles or session identifier
     */
    public static IdentityContext anonymous() { return new IdentityContext(null, AccountType.ANONYMOUS, Collections.<String>emptySet(), null); }

    /** @return the sanitized host user identifier, or {@code null} when unauthenticated */
    public String getUserId() { return userId; }

    /** @return the class of authenticated principal */
    public AccountType getAccountType() { return accountType; }

    /** @return immutable host role identifiers */
    public Set<String> getRoleIds() { return roleIds; }

    /** @return one-way session identifier, or {@code null} when unavailable */
    public String getSessionIdHash() { return sessionIdHash; }
}

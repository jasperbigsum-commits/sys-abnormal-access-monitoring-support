package io.github.jasper.monitoring.api;

/**
 * Immutable result of the host application's resource-scope authorization check.
 *
 * <p>A denial may include a stable reason code suitable for security events and audit records.</p>
 */
public final class AuthorizationDecision {
    private final boolean allowed;
    private final String reasonCode;
    private AuthorizationDecision(boolean allowed, String reasonCode) {
        this.allowed = allowed;
        this.reasonCode = SecurityFieldSanitizer.text(reasonCode, 128);
    }
    /**
     * Creates an allow decision without a denial reason.
     *
     * @return an allowed decision
     */
    public static AuthorizationDecision allowed() { return new AuthorizationDecision(true, null); }

    /**
     * Creates a deny decision.
     *
     * @param reasonCode a non-sensitive, stable code explaining the denial
     * @return a denied decision
     */
    public static AuthorizationDecision denied(String reasonCode) { return new AuthorizationDecision(false, reasonCode); }

    /** @return whether the host allows access to the requested resource scope */
    public boolean isAllowed() { return allowed; }

    /** @return the sanitized denial reason, or {@code null} for an allow decision */
    public String getReasonCode() { return reasonCode; }
}

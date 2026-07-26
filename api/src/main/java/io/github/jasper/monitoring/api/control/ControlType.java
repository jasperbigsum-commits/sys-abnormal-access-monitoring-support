package io.github.jasper.monitoring.api.control;

import io.github.jasper.monitoring.api.ControlActionType;

/** Registered executable control action. */
public enum ControlType {
    REQUIRE_CAPTCHA,
    RATE_LIMIT,
    LOCK,
    REVOKE_SESSION,
    REQUIRE_MFA,
    DENY,
    REQUIRE_APPROVAL;

    public boolean requiresApproval() { return this == REQUIRE_APPROVAL; }

    /** Explicit migration mapping from the legacy public enum. */
    public static ControlType from(ControlActionType legacy) {
        switch (legacy) {
            case REQUIRE_CAPTCHA: return REQUIRE_CAPTCHA;
            case RATE_LIMIT: return RATE_LIMIT;
            case LOCK: return LOCK;
            case REVOKE_SESSION: return REVOKE_SESSION;
            case REQUIRE_MFA: return REQUIRE_MFA;
            case DENY: return DENY;
            case REQUIRE_APPROVAL: return REQUIRE_APPROVAL;
            case RECORD:
            default: throw new IllegalArgumentException("RECORD is not an executable control type");
        }
    }
}

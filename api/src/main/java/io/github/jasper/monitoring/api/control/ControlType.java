package io.github.jasper.monitoring.api.control;

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
}

package io.github.jasper.monitoring.api;

/**
 * A response the monitoring component can request from a host-provided control handler.
 */
public enum ControlActionType {
    /** Retain evidence without changing request handling. */
    RECORD,
    /** Require a challenge before the next sensitive action. */
    REQUIRE_CAPTCHA,
    /** Temporarily restrict request throughput. */
    RATE_LIMIT,
    /** Lock an account or principal according to host policy. */
    LOCK,
    /** Invalidate one or more active sessions. */
    REVOKE_SESSION,
    /** Require a stronger authentication step. */
    REQUIRE_MFA,
    /** Deny the relevant request or access scope. */
    DENY,
    /** Require a host-defined approval workflow. */
    REQUIRE_APPROVAL
}

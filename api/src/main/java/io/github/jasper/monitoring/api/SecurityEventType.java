package io.github.jasper.monitoring.api;

/**
 * Normalized categories of server-observed security activity.
 *
 * <p>Choose the most specific category available so built-in rules and audit reports remain
 * consistent across host systems.</p>
 */
public enum SecurityEventType {
    LOGIN_SUCCESS,
    LOGIN_FAILURE,
    MFA_FAILURE,
    LOGOUT,
    SESSION_CREATED,
    SESSION_REVOKED,
    SESSION_CONCURRENT,
    ACCESS_ALLOWED,
    ACCESS_DENIED,
    RESOURCE_SCOPE_DENIED,
    QUERY,
    VIEW_SENSITIVE,
    EXPORT,
    BULK_OPERATION,
    CREATE,
    UPDATE,
    DELETE,
    BATCH_UPDATE,
    ROLE_GRANT,
    ROLE_REVOKE,
    ADMIN_CREATE,
    ACCOUNT_DISABLE,
    RULE_CHANGE,
    AUDIT_CONFIG_CHANGE,
    SECURITY_SWITCH_CHANGE
}

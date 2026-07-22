package io.github.jasper.monitoring.api;

/**
 * Classifies the principal associated with a monitored request.
 *
 * <p>The host application derives this value from its authentication system;
 * browser-provided values must never be used.</p>
 */
public enum AccountType {
    /** An authenticated human user. */
    PERSON,
    /** A service, workload, or machine identity. */
    SERVICE,
    /** A request for which no authenticated principal is available. */
    ANONYMOUS
}

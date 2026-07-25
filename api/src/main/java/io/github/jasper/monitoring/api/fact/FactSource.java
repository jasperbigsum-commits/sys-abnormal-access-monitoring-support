package io.github.jasper.monitoring.api.fact;

/** Identifies the origin and trust boundary of a monitoring fact. */
public enum FactSource {
    /** Trusted server-side request data. */
    TRUSTED_REQUEST,
    /** Trusted server-side identity data. */
    TRUSTED_IDENTITY,
    /** A value extracted from a bound method parameter. */
    METHOD_PARAMETER,
    /** A value supplied by an explicitly bound host provider. */
    HOST_PROVIDER,
    /** Supplemental client telemetry that is not authoritative. */
    CLIENT_SUPPLEMENTAL,
    /** A value established by framework outcome processing. */
    FRAMEWORK_OUTCOME
}

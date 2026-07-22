package io.github.jasper.monitoring.web;

/**
 * Canonical HTTP header names for optional browser telemetry correlation.
 *
 * <p>These headers are correlation hints only. They must not be used as the source of identity,
 * client IP, authorization, risk, or control decisions.</p>
 */
public final class FrontendContractHeaders {
    /** Request correlation identifier propagated between browser and server. */
    public static final String REQUEST_ID = "X-Security-Monitor-Request-Id";
    /** Distributed tracing identifier propagated between browser and server. */
    public static final String TRACE_ID = "X-Security-Monitor-Trace-Id";
    /** One-way browser device identifier, when an approved implementation provides one. */
    public static final String DEVICE_ID = "X-Security-Monitor-Device-Id";
    /** Browser event timestamp for correlation and clock-skew validation. */
    public static final String CLIENT_TIME = "X-Security-Monitor-Client-Time";
    /** Browser route or page associated with the signal. */
    public static final String PAGE = "X-Security-Monitor-Page";
    /** Browser interaction name associated with the signal. */
    public static final String ACTION = "X-Security-Monitor-Action";
    private FrontendContractHeaders() { }
}

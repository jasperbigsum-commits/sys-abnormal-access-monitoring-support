package io.github.jasper.monitoring.api;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Servlet-independent, sanitized request facts collected by a framework adapter.
 *
 * <p>Only trusted headers should be included. Identity, authorization, and source-address
 * decisions remain server-side responsibilities.</p>
 */
public final class MonitoringRequestContext {
    private final String method;
    private final String path;
    private final String sourceIp;
    private final String requestId;
    private final String traceId;
    private final Map<String, String> trustedHeaders;
    private MonitoringRequestContext(Builder builder) {
        method = required(builder.method, "method", 16);
        path = required(builder.path, "path", 512);
        sourceIp = required(builder.sourceIp, "sourceIp", 128);
        requestId = required(builder.requestId, "requestId", 128);
        traceId = SecurityFieldSanitizer.text(builder.traceId, 128);
        trustedHeaders = Collections.unmodifiableMap(new LinkedHashMap<String, String>(builder.trustedHeaders));
    }
    /** @return a builder for an immutable request-context snapshot */
    public static Builder builder() { return new Builder(); }

    /** @return normalized HTTP method */
    public String getMethod() { return method; }

    /** @return normalized request path */
    public String getPath() { return path; }

    /** @return trusted client IP address resolved by the adapter */
    public String getSourceIp() { return sourceIp; }

    /** @return request correlation identifier */
    public String getRequestId() { return requestId; }

    /** @return distributed-tracing identifier, or {@code null} when unavailable */
    public String getTraceId() { return traceId; }

    /** @return immutable, host-approved headers used for correlation */
    public Map<String, String> getTrustedHeaders() { return trustedHeaders; }
    private static String required(String value, String field, int length) {
        String sanitized = SecurityFieldSanitizer.text(value, length);
        if (sanitized == null || sanitized.isEmpty()) { throw new IllegalArgumentException(field + " is required"); }
        return sanitized;
    }
    /** Builder for {@link MonitoringRequestContext}. */
    public static final class Builder {
        private String method;
        private String path;
        private String sourceIp;
        private String requestId;
        private String traceId;
        private final Map<String, String> trustedHeaders = new LinkedHashMap<String, String>();
        /**
         * Sets the HTTP method.
         *
         * @param value HTTP method
         * @return this builder
         */
        public Builder method(String value) { method = value; return this; }
        /**
         * Sets the request path.
         *
         * @param value request path
         * @return this builder
         */
        public Builder path(String value) { path = value; return this; }
        /**
         * Sets the trusted client IP address.
         *
         * @param value trusted client IP address
         * @return this builder
         */
        public Builder sourceIp(String value) { sourceIp = value; return this; }
        /**
         * Sets the request correlation identifier.
         *
         * @param value request correlation identifier
         * @return this builder
         */
        public Builder requestId(String value) { requestId = value; return this; }
        /**
         * Sets the tracing identifier.
         *
         * @param value tracing identifier, when available
         * @return this builder
         */
        public Builder traceId(String value) { traceId = value; return this; }

        /**
         * Adds a host-approved header for correlation.
         *
         * @param key non-sensitive header name
         * @param value sanitized header value
         * @return this builder
         */
        public Builder trustedHeader(String key, String value) {
            SecurityFieldSanitizer.requireSafeAttributeKey(key);
            trustedHeaders.put(SecurityFieldSanitizer.text(key, 128), SecurityFieldSanitizer.text(value, 512));
            return this;
        }
        /**
         * Builds an immutable context after validating required request facts.
         *
         * @return immutable request context
         * @throws IllegalArgumentException if required facts are missing or unsafe
         */
        public MonitoringRequestContext build() { return new MonitoringRequestContext(this); }
    }
}

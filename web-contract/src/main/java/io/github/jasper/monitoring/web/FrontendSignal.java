package io.github.jasper.monitoring.web;

import io.github.jasper.monitoring.api.error.MonitoringErrorCode;
import io.github.jasper.monitoring.api.error.MonitoringValidationException;

import io.github.jasper.monitoring.api.SecurityFieldSanitizer;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Immutable browser-provided supplemental evidence.
 *
 * <p>It intentionally contains no identity, IP address, session, authorization, risk, or control
 * assertions. Device and resource identifiers, when present, must use a {@code sha256:} prefix.
 * Attributes are restricted to the published allowlist so the browser cannot inject arbitrary
 * evidence fields.</p>
 */
public final class FrontendSignal {
    private static final Set<String> ALLOWED_ATTRIBUTES = Collections.unmodifiableSet(new LinkedHashSet<String>(Arrays.asList(
        "feature", "interaction", "page_type", "network_type", "ui_version", "resource_class", "outcome_hint")));
    private final String contractVersion;
    private final String clientEventId;
    private final Instant occurredAt;
    private final String requestId;
    private final String traceId;
    private final String route;
    private final String action;
    private final String deviceIdHash;
    private final String resourceType;
    private final String resourceIdHash;
    private final Map<String, String> attributes;

    private FrontendSignal(Builder builder) {
        contractVersion = required(builder.contractVersion == null ? "1.0" : builder.contractVersion, "contractVersion", 16);
        clientEventId = required(builder.clientEventId, "clientEventId", 128);
        occurredAt = required(builder.occurredAt, "occurredAt");
        requestId = required(builder.requestId, "requestId", 128);
        traceId = SecurityFieldSanitizer.text(builder.traceId, 128);
        route = required(builder.route, "route", 512);
        action = required(builder.action, "action", 128);
        deviceIdHash = requireHash(builder.deviceIdHash, "deviceIdHash");
        resourceType = SecurityFieldSanitizer.text(builder.resourceType, 128);
        resourceIdHash = requireHash(builder.resourceIdHash, "resourceIdHash");
        attributes = safeAttributes(builder.attributes);
    }
    /** @return a builder for a validated browser signal */
    public static Builder builder() { return new Builder(); }

    /** @return protocol version, defaulting to {@code 1.0} */
    public String getContractVersion() { return contractVersion; }
    /** @return browser-generated event identifier for client-side deduplication */
    public String getClientEventId() { return clientEventId; }
    /** @return time reported by the browser, subject to server-side skew validation */
    public Instant getOccurredAt() { return occurredAt; }
    /** @return request correlation identifier supplied by the server to the browser */
    public String getRequestId() { return requestId; }
    /** @return tracing identifier, or {@code null} when unavailable */
    public String getTraceId() { return traceId; }
    /** @return browser route associated with the interaction */
    public String getRoute() { return route; }
    /** @return browser interaction name */
    public String getAction() { return action; }
    /** @return optional {@code sha256:}-prefixed device identifier */
    public String getDeviceIdHash() { return deviceIdHash; }
    /** @return optional logical resource category */
    public String getResourceType() { return resourceType; }
    /** @return optional {@code sha256:}-prefixed resource identifier */
    public String getResourceIdHash() { return resourceIdHash; }
    /** @return immutable allowlisted client attributes */
    public Map<String, String> getAttributes() { return attributes; }

    private static String requireHash(String value, String name) {
        String sanitized = SecurityFieldSanitizer.text(value, 256);
        if (sanitized == null || sanitized.isEmpty()) { return null; }
        if (!sanitized.startsWith("sha256:")) {
            throw new MonitoringValidationException(MonitoringErrorCode.INVALID_FIELD_VALUE,
                name + " must use a sha256: prefix");
        }
        return sanitized;
    }
    private static String required(String value, String name, int maximumLength) {
        String sanitized = SecurityFieldSanitizer.text(value, maximumLength);
        if (sanitized == null || sanitized.isEmpty()) {
            throw new MonitoringValidationException(MonitoringErrorCode.REQUIRED_FIELD_MISSING,
                name + " is required");
        }
        return sanitized;
    }
    private static <T> T required(T value, String name) {
        if (value == null) {
            throw new MonitoringValidationException(MonitoringErrorCode.REQUIRED_FIELD_MISSING,
                name + " is required");
        }
        return value;
    }
    private static Map<String, String> safeAttributes(Map<String, String> input) {
        Map<String, String> result = new LinkedHashMap<String, String>();
        for (Map.Entry<String, String> entry : input.entrySet()) {
            String key = SecurityFieldSanitizer.text(entry.getKey(), 64);
            if (!ALLOWED_ATTRIBUTES.contains(key)) {
                throw new MonitoringValidationException(MonitoringErrorCode.UNSAFE_EVENT_ATTRIBUTE,
                    "Unsupported frontend attribute");
            }
            result.put(key, SecurityFieldSanitizer.text(entry.getValue(), 256));
        }
        return Collections.unmodifiableMap(result);
    }
    /** Builder for {@link FrontendSignal}. */
    public static final class Builder {
        private String contractVersion;
        private String clientEventId;
        private Instant occurredAt;
        private String requestId;
        private String traceId;
        private String route;
        private String action;
        private String deviceIdHash;
        private String resourceType;
        private String resourceIdHash;
        private final Map<String, String> attributes = new LinkedHashMap<String, String>();
        /**
         * Sets the client contract version.
         *
         * @param value version supplied by the browser
         * @return this builder
         */
        public Builder contractVersion(String value) { contractVersion = value; return this; }
        /**
         * Sets a browser-generated event identifier.
         *
         * @param value client event identifier
         * @return this builder
         */
        public Builder clientEventId(String value) { clientEventId = value; return this; }
        /**
         * Sets the time reported by the browser.
         *
         * @param value browser event time
         * @return this builder
         */
        public Builder occurredAt(Instant value) { occurredAt = value; return this; }
        /**
         * Sets the request correlation identifier supplied by the server.
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
         * Sets the browser route.
         *
         * @param value browser route
         * @return this builder
         */
        public Builder route(String value) { route = value; return this; }
        /**
         * Sets the browser interaction name.
         *
         * @param value browser interaction name
         * @return this builder
         */
        public Builder action(String value) { action = value; return this; }
        /**
         * Sets a one-way browser device identifier.
         *
         * @param value optional identifier using the {@code sha256:} prefix
         * @return this builder
         */
        public Builder deviceIdHash(String value) { deviceIdHash = value; return this; }
        /**
         * Sets the logical resource category.
         *
         * @param value optional resource category
         * @return this builder
         */
        public Builder resourceType(String value) { resourceType = value; return this; }
        /**
         * Sets a one-way resource identifier.
         *
         * @param value optional identifier using the {@code sha256:} prefix
         * @return this builder
         */
        public Builder resourceIdHash(String value) { resourceIdHash = value; return this; }
        /**
         * Adds an allowlisted browser attribute.
         *
         * @param key one of the frontend contract's supported attribute names
         * @param value non-sensitive attribute value
         * @return this builder
         */
        public Builder attribute(String key, String value) { attributes.put(key, value); return this; }
        /**
         * Validates the signal and returns its immutable representation.
         *
         * @return validated browser signal
         * @throws IllegalArgumentException if required fields, hashes, or attributes are invalid
         */
        public FrontendSignal build() { return new FrontendSignal(this); }
    }
}

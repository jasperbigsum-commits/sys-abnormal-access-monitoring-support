package io.github.jasper.monitoring.api;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable, sanitized event supplied by a host integration for monitoring.
 *
 * <p>The monitor assigns persistence identifiers and evaluates the draft using trusted server
 * facts. Do not place credentials, raw session IDs, cookies, request bodies, or other sensitive
 * payloads in this model; use one-way hashes and stable reason codes instead.</p>
 */
public final class SecurityEventDraft {
    private final SecurityEventType eventType;
    private final String action;
    private final SecurityEventResult result;
    private final String sourceIp;
    private final String requestId;
    private final String traceId;
    private final String userId;
    private final AccountType accountType;
    private final Set<String> roleIds;
    private final String sessionIdHash;
    private final String deviceIdHash;
    private final String resourceType;
    private final String resourceId;
    private final String orgScope;
    private final long dataCount;
    private final long latencyMs;
    private final Instant occurredAt;
    private final String reasonCode;
    private final Map<String, String> attributes;

    private SecurityEventDraft(Builder builder) {
        this.eventType = required(builder.eventType, "eventType");
        this.action = requiredText(builder.action, "action", 128);
        this.result = required(builder.result, "result");
        this.sourceIp = requiredText(builder.sourceIp, "sourceIp", 128);
        this.requestId = requiredText(builder.requestId, "requestId", 128);
        this.traceId = SecurityFieldSanitizer.text(builder.traceId, 128);
        this.userId = SecurityFieldSanitizer.text(builder.userId, 128);
        this.accountType = builder.accountType == null ? AccountType.PERSON : builder.accountType;
        this.roleIds = immutableRoles(builder.roleIds);
        this.sessionIdHash = SecurityFieldSanitizer.text(builder.sessionIdHash, 256);
        this.deviceIdHash = SecurityFieldSanitizer.text(builder.deviceIdHash, 256);
        this.resourceType = SecurityFieldSanitizer.text(builder.resourceType, 128);
        this.resourceId = SecurityFieldSanitizer.text(builder.resourceId, 256);
        this.orgScope = SecurityFieldSanitizer.text(builder.orgScope, 256);
        this.dataCount = nonNegative(builder.dataCount, "dataCount");
        this.latencyMs = nonNegative(builder.latencyMs, "latencyMs");
        this.occurredAt = required(builder.occurredAt, "occurredAt");
        this.reasonCode = SecurityFieldSanitizer.text(builder.reasonCode, 128);
        this.attributes = immutableAttributes(builder.attributes);
    }

    /**
     * Starts a new event draft.
     *
     * @return builder that validates and sanitizes values when {@link Builder#build()} is called
     */
    public static Builder builder() {
        return new Builder();
    }

    /** @return normalized security activity category */
    public SecurityEventType getEventType() { return eventType; }
    /** @return sanitized operation name */
    public String getAction() { return action; }
    /** @return final server-side operation outcome */
    public SecurityEventResult getResult() { return result; }
    /** @return trusted client address */
    public String getSourceIp() { return sourceIp; }
    /** @return request correlation identifier */
    public String getRequestId() { return requestId; }
    /** @return distributed tracing identifier, or {@code null} when unavailable */
    public String getTraceId() { return traceId; }
    /** @return server-resolved user identifier, or {@code null} when anonymous */
    public String getUserId() { return userId; }
    /** @return account class associated with the event */
    public AccountType getAccountType() { return accountType; }
    /** @return immutable host role identifiers */
    public Set<String> getRoleIds() { return roleIds; }
    /** @return one-way session identifier, or {@code null} when unavailable */
    public String getSessionIdHash() { return sessionIdHash; }
    /** @return one-way device identifier, or {@code null} when unavailable */
    public String getDeviceIdHash() { return deviceIdHash; }
    /** @return logical resource category, or {@code null} when unavailable */
    public String getResourceType() { return resourceType; }
    /** @return resource identifier, or {@code null} when unavailable */
    public String getResourceId() { return resourceId; }
    /** @return tenant, organization, or data-domain boundary, or {@code null} when unavailable */
    public String getOrgScope() { return orgScope; }
    /** @return number of affected records, when known */
    public long getDataCount() { return dataCount; }
    /** @return measured operation latency in milliseconds, when known */
    public long getLatencyMs() { return latencyMs; }
    /** @return server-observed event time */
    public Instant getOccurredAt() { return occurredAt; }
    /** @return stable, non-sensitive reason code, or {@code null} when unavailable */
    public String getReasonCode() { return reasonCode; }
    /** @return immutable, sanitized non-sensitive extension attributes */
    public Map<String, String> getAttributes() { return attributes; }
    /**
     * Gets one non-sensitive extension attribute.
     *
     * @param key attribute name
     * @return sanitized attribute value, or {@code null} when absent
     */
    public String getAttribute(String key) { return attributes.get(key); }

    private static long nonNegative(long value, String name) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " must not be negative");
        }
        return value;
    }

    private static String requiredText(String value, String name, int maximumLength) {
        String sanitized = SecurityFieldSanitizer.text(value, maximumLength);
        if (sanitized == null || sanitized.isEmpty()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return sanitized;
    }

    private static <T> T required(T value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value;
    }

    private static Set<String> immutableRoles(Set<String> roles) {
        Set<String> sanitized = new LinkedHashSet<String>();
        if (roles != null) {
            for (String role : roles) {
                String value = SecurityFieldSanitizer.text(role, 128);
                if (value != null && !value.isEmpty()) {
                    sanitized.add(value);
                }
            }
        }
        return Collections.unmodifiableSet(sanitized);
    }

    private static Map<String, String> immutableAttributes(Map<String, String> attributes) {
        Map<String, String> sanitized = new LinkedHashMap<String, String>();
        if (attributes != null) {
            for (Map.Entry<String, String> entry : attributes.entrySet()) {
                SecurityFieldSanitizer.requireSafeAttributeKey(entry.getKey());
                String key = SecurityFieldSanitizer.text(entry.getKey(), 128);
                String value = SecurityFieldSanitizer.text(entry.getValue(), 512);
                if (value != null) {
                    sanitized.put(key, value);
                }
            }
        }
        return Collections.unmodifiableMap(sanitized);
    }

    /** Builder for {@link SecurityEventDraft}. */
    public static final class Builder {
        private SecurityEventType eventType;
        private String action;
        private SecurityEventResult result;
        private String sourceIp;
        private String requestId;
        private String traceId;
        private String userId;
        private AccountType accountType;
        private Set<String> roleIds = new LinkedHashSet<String>();
        private String sessionIdHash;
        private String deviceIdHash;
        private String resourceType;
        private String resourceId;
        private String orgScope;
        private long dataCount;
        private long latencyMs;
        private Instant occurredAt;
        private String reasonCode;
        private Map<String, String> attributes = new LinkedHashMap<String, String>();

        /**
         * Sets the normalized security activity category.
         *
         * @param value normalized security activity category
         * @return this builder
         */
        public Builder eventType(SecurityEventType value) { this.eventType = value; return this; }
        /**
         * Sets the server operation name.
         *
         * @param value server operation name
         * @return this builder
         */
        public Builder action(String value) { this.action = value; return this; }
        /**
         * Sets the final server-side operation outcome.
         *
         * @param value final server-side operation outcome
         * @return this builder
         */
        public Builder result(SecurityEventResult value) { this.result = value; return this; }
        /**
         * Sets the trusted client IP address.
         *
         * @param value trusted client IP address
         * @return this builder
         */
        public Builder sourceIp(String value) { this.sourceIp = value; return this; }
        /**
         * Sets the request correlation identifier.
         *
         * @param value request correlation identifier
         * @return this builder
         */
        public Builder requestId(String value) { this.requestId = value; return this; }
        /**
         * Sets the distributed tracing identifier.
         *
         * @param value distributed tracing identifier
         * @return this builder
         */
        public Builder traceId(String value) { this.traceId = value; return this; }
        /**
         * Sets the server-resolved user identifier.
         *
         * @param value server-resolved user identifier
         * @return this builder
         */
        public Builder userId(String value) { this.userId = value; return this; }
        /**
         * Sets the principal class.
         *
         * @param value principal class
         * @return this builder
         */
        public Builder accountType(AccountType value) { this.accountType = value; return this; }
        /**
         * Adds one host role identifier.
         *
         * @param value one host role identifier to add
         * @return this builder
         */
        public Builder roleId(String value) { if (value != null) { this.roleIds.add(value); } return this; }
        /**
         * Replaces role identifiers with a defensive copy.
         *
         * @param value host role identifiers; copied by this builder
         * @return this builder
         */
        public Builder roleIds(Set<String> value) { this.roleIds = value == null ? new LinkedHashSet<String>() : new LinkedHashSet<String>(value); return this; }
        /**
         * Sets a one-way session identifier.
         *
         * @param value one-way session identifier
         * @return this builder
         */
        public Builder sessionIdHash(String value) { this.sessionIdHash = value; return this; }
        /**
         * Sets a one-way device identifier.
         *
         * @param value one-way device identifier
         * @return this builder
         */
        public Builder deviceIdHash(String value) { this.deviceIdHash = value; return this; }
        /**
         * Sets the logical resource category.
         *
         * @param value logical resource category
         * @return this builder
         */
        public Builder resourceType(String value) { this.resourceType = value; return this; }
        /**
         * Sets the host resource identifier.
         *
         * @param value host resource identifier
         * @return this builder
         */
        public Builder resourceId(String value) { this.resourceId = value; return this; }
        /**
         * Sets the tenant, organization, or data-domain boundary.
         *
         * @param value tenant, organization, or data-domain boundary
         * @return this builder
         */
        public Builder orgScope(String value) { this.orgScope = value; return this; }
        /**
         * Sets the affected record count.
         *
         * @param value affected record count; must not be negative
         * @return this builder
         */
        public Builder dataCount(long value) { this.dataCount = value; return this; }
        /**
         * Sets the measured latency.
         *
         * @param value measured latency in milliseconds; must not be negative
         * @return this builder
         */
        public Builder latencyMs(long value) { this.latencyMs = value; return this; }
        /**
         * Sets the server-observed event time.
         *
         * @param value server-observed event time
         * @return this builder
         */
        public Builder occurredAt(Instant value) { this.occurredAt = value; return this; }
        /**
         * Sets a stable, non-sensitive reason code.
         *
         * @param value stable, non-sensitive reason code
         * @return this builder
         */
        public Builder reasonCode(String value) { this.reasonCode = value; return this; }
        /**
         * Adds an approved, non-sensitive attribute.
         *
         * @param key attribute key; keys naming credential material are rejected during build
         * @param value attribute value
         * @return this builder
         */
        public Builder attribute(String key, String value) { this.attributes.put(key, value); return this; }
        /**
         * Replaces extension attributes with a copied map.
         *
         * @param value non-sensitive attributes, or {@code null} for an empty map
         * @return this builder
         */
        public Builder attributes(Map<String, String> value) { this.attributes = value == null ? new LinkedHashMap<String, String>() : new LinkedHashMap<String, String>(value); return this; }
        /**
         * Validates required fields and returns an immutable sanitized draft.
         *
         * @return monitoring-ready event draft
         * @throws IllegalArgumentException if required fields are missing, counts are negative, or attributes are unsafe
         */
        public SecurityEventDraft build() { return new SecurityEventDraft(this); }
    }
}

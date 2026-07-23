package io.github.jasper.monitoring.api;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.OptionalLong;

/** Immutable, sanitized facts that a {@link MonitorActionEnricher} may contribute. */
public final class MonitorActionFacts {
    private final String resourceId;
    private final String orgScope;
    private final OptionalLong dataCount;
    private final OptionalLong latencyMs;
    private final SecurityEventResult result;
    private final String reasonCode;
    private final Map<String, String> attributes;

    private MonitorActionFacts(Builder builder) {
        this.resourceId = SecurityFieldSanitizer.text(builder.resourceId, 256);
        this.orgScope = SecurityFieldSanitizer.text(builder.orgScope, 256);
        this.dataCount = optionalNonNegative(builder.dataCount, "dataCount");
        this.latencyMs = optionalNonNegative(builder.latencyMs, "latencyMs");
        this.result = builder.result;
        this.reasonCode = SecurityFieldSanitizer.text(builder.reasonCode, 128);
        this.attributes = immutableAttributes(builder.attributes);
    }

    /** @return a facts value with no dynamic fields */
    public static MonitorActionFacts empty() {
        return builder().build();
    }

    /** @return a builder for approved dynamic facts */
    public static Builder builder() {
        return new Builder();
    }

    /** @return sanitized dynamic resource identifier, or {@code null} */
    public String getResourceId() {
        return resourceId;
    }

    /** @return sanitized dynamic organization scope, or {@code null} */
    public String getOrgScope() {
        return orgScope;
    }

    /** @return affected data count when the host supplied one */
    public OptionalLong getDataCount() {
        return dataCount;
    }

    /** @return elapsed action latency when the host supplied one */
    public OptionalLong getLatencyMs() {
        return latencyMs;
    }

    /** @return host-supplied business result, or {@code null} */
    public SecurityEventResult getResult() {
        return result;
    }

    /** @return sanitized stable reason code, or {@code null} */
    public String getReasonCode() {
        return reasonCode;
    }

    /** @return immutable approved extension attributes */
    public Map<String, String> getAttributes() {
        return attributes;
    }

    private static OptionalLong optionalNonNegative(Long value, String name) {
        if (value == null) {
            return OptionalLong.empty();
        }
        if (value.longValue() < 0) {
            throw new IllegalArgumentException(name + " must not be negative");
        }
        return OptionalLong.of(value.longValue());
    }

    private static Map<String, String> immutableAttributes(Map<String, String> values) {
        Map<String, String> sanitized = new LinkedHashMap<String, String>();
        for (Map.Entry<String, String> entry : values.entrySet()) {
            SecurityFieldSanitizer.requireSafeAttributeKey(entry.getKey());
            String key = SecurityFieldSanitizer.text(entry.getKey(), 128);
            String value = SecurityFieldSanitizer.text(entry.getValue(), 512);
            if (value != null) {
                sanitized.put(key, value);
            }
        }
        return Collections.unmodifiableMap(sanitized);
    }

    /** Builder for {@link MonitorActionFacts}. */
    public static final class Builder {
        private String resourceId;
        private String orgScope;
        private Long dataCount;
        private Long latencyMs;
        private SecurityEventResult result;
        private String reasonCode;
        private final Map<String, String> attributes = new LinkedHashMap<String, String>();

        /** @param value dynamic resource identifier */
        public Builder resourceId(String value) {
            this.resourceId = value;
            return this;
        }

        /** @param value dynamic organization scope */
        public Builder orgScope(String value) {
            this.orgScope = value;
            return this;
        }

        /** @param value affected data count; must not be negative */
        public Builder dataCount(long value) {
            this.dataCount = Long.valueOf(value);
            return this;
        }

        /** @param value action latency in milliseconds; must not be negative */
        public Builder latencyMs(long value) {
            this.latencyMs = Long.valueOf(value);
            return this;
        }

        /** @param value host-confirmed business result */
        public Builder result(SecurityEventResult value) {
            this.result = value;
            return this;
        }

        /** @param value stable non-sensitive reason code */
        public Builder reasonCode(String value) {
            this.reasonCode = value;
            return this;
        }

        /**
         * Adds an approved non-sensitive extension attribute.
         *
         * @param key attribute key; checked during build
         * @param value attribute value
         * @return this builder
         */
        public Builder attribute(String key, String value) {
            attributes.put(key, value);
            return this;
        }

        /** @return immutable sanitized dynamic facts */
        public MonitorActionFacts build() {
            return new MonitorActionFacts(this);
        }
    }
}

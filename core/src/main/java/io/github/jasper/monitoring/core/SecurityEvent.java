package io.github.jasper.monitoring.core;

import io.github.jasper.monitoring.api.AccountType;
import io.github.jasper.monitoring.api.SecurityEventDraft;
import io.github.jasper.monitoring.api.SecurityEventResult;
import io.github.jasper.monitoring.api.SecurityEventType;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Immutable, server-stamped security event used for detection and audit.
 * Event attributes must already have passed the API layer's sensitive-data and log-safety validation.
 */
public final class SecurityEvent {
    private final String eventId;
    private final String systemId;
    private final SecurityEventType eventType;
    private final Instant occurredAt;
    private final Instant receivedAt;
    private final String userId;
    private final AccountType accountType;
    private final Set<String> roleIds;
    private final String sourceIp;
    private final String deviceIdHash;
    private final String sessionIdHash;
    private final String requestId;
    private final String traceId;
    private final String action;
    private final SecurityEventResult result;
    private final String reasonCode;
    private final String resourceType;
    private final String resourceId;
    private final String orgScope;
    private final long dataCount;
    private final long latencyMs;
    private final Map<String, String> attributes;

    private SecurityEvent(Builder builder) {
        this.eventId = builder.eventId;
        this.systemId = builder.systemId;
        this.eventType = builder.eventType;
        this.occurredAt = builder.occurredAt;
        this.receivedAt = builder.receivedAt;
        this.userId = builder.userId;
        this.accountType = builder.accountType;
        this.roleIds = Collections.unmodifiableSet(new LinkedHashSet<String>(builder.roleIds));
        this.sourceIp = builder.sourceIp;
        this.deviceIdHash = builder.deviceIdHash;
        this.sessionIdHash = builder.sessionIdHash;
        this.requestId = builder.requestId;
        this.traceId = builder.traceId;
        this.action = builder.action;
        this.result = builder.result;
        this.reasonCode = builder.reasonCode;
        this.resourceType = builder.resourceType;
        this.resourceId = builder.resourceId;
        this.orgScope = builder.orgScope;
        this.dataCount = builder.dataCount;
        this.latencyMs = builder.latencyMs;
        this.attributes = Collections.unmodifiableMap(new LinkedHashMap<String, String>(builder.attributes));
    }

    /**
     * Converts a validated host draft into the persisted event form.
     *
     * @param draft validated input from a trusted server-side integration point
     * @param systemId configured identifier of the emitting system
     * @param eventId server-generated immutable event identifier
     * @param receivedAt server timestamp assigned when the event is accepted
     * @return immutable event ready for persistence and rule evaluation
     */
    public static SecurityEvent from(SecurityEventDraft draft, String systemId, String eventId, Instant receivedAt) {
        return builder()
            .eventId(eventId).systemId(systemId).eventType(draft.getEventType())
            .occurredAt(draft.getOccurredAt()).receivedAt(receivedAt)
            .userId(draft.getUserId()).accountType(draft.getAccountType()).roleIds(draft.getRoleIds())
            .sourceIp(draft.getSourceIp()).deviceIdHash(draft.getDeviceIdHash()).sessionIdHash(draft.getSessionIdHash())
            .requestId(draft.getRequestId()).traceId(draft.getTraceId()).action(draft.getAction()).result(draft.getResult())
            .reasonCode(draft.getReasonCode()).resourceType(draft.getResourceType()).resourceId(draft.getResourceId())
            .orgScope(draft.getOrgScope()).dataCount(draft.getDataCount()).latencyMs(draft.getLatencyMs())
            .attributes(draft.getAttributes()).build();
    }

    /** @return a builder primarily used by repository adapters to rehydrate persisted events */
    public static Builder builder() { return new Builder(); }
    public String getEventId() { return eventId; }
    public String getSystemId() { return systemId; }
    public SecurityEventType getEventType() { return eventType; }
    public Instant getOccurredAt() { return occurredAt; }
    public Instant getReceivedAt() { return receivedAt; }
    public String getUserId() { return userId; }
    public AccountType getAccountType() { return accountType; }
    public Set<String> getRoleIds() { return roleIds; }
    public String getSourceIp() { return sourceIp; }
    public String getDeviceIdHash() { return deviceIdHash; }
    public String getSessionIdHash() { return sessionIdHash; }
    public String getRequestId() { return requestId; }
    public String getTraceId() { return traceId; }
    public String getAction() { return action; }
    public SecurityEventResult getResult() { return result; }
    public String getReasonCode() { return reasonCode; }
    public String getResourceType() { return resourceType; }
    public String getResourceId() { return resourceId; }
    public String getOrgScope() { return orgScope; }
    public long getDataCount() { return dataCount; }
    public long getLatencyMs() { return latencyMs; }
    public Map<String, String> getAttributes() { return attributes; }
    public String getAttribute(String key) { return attributes.get(key); }

    /** @return user ID when available, otherwise the source IP used as an anonymous subject */
    public String subject() {
        return userId == null || userId.isEmpty() ? sourceIp : userId;
    }

    /** Mutable builder for reconstructing an immutable security event from a validated source. */
    public static final class Builder {
        private String eventId;
        private String systemId;
        private SecurityEventType eventType;
        private Instant occurredAt;
        private Instant receivedAt;
        private String userId;
        private AccountType accountType = AccountType.PERSON;
        private Set<String> roleIds = new LinkedHashSet<String>();
        private String sourceIp;
        private String deviceIdHash;
        private String sessionIdHash;
        private String requestId;
        private String traceId;
        private String action;
        private SecurityEventResult result;
        private String reasonCode;
        private String resourceType;
        private String resourceId;
        private String orgScope;
        private long dataCount;
        private long latencyMs;
        private Map<String, String> attributes = new LinkedHashMap<String, String>();
        public Builder eventId(String value) { eventId = value; return this; }
        public Builder systemId(String value) { systemId = value; return this; }
        public Builder eventType(SecurityEventType value) { eventType = value; return this; }
        public Builder occurredAt(Instant value) { occurredAt = value; return this; }
        public Builder receivedAt(Instant value) { receivedAt = value; return this; }
        public Builder userId(String value) { userId = value; return this; }
        public Builder accountType(AccountType value) { accountType = value; return this; }
        public Builder roleIds(Set<String> value) { roleIds = value == null ? new LinkedHashSet<String>() : value; return this; }
        public Builder sourceIp(String value) { sourceIp = value; return this; }
        public Builder deviceIdHash(String value) { deviceIdHash = value; return this; }
        public Builder sessionIdHash(String value) { sessionIdHash = value; return this; }
        public Builder requestId(String value) { requestId = value; return this; }
        public Builder traceId(String value) { traceId = value; return this; }
        public Builder action(String value) { action = value; return this; }
        public Builder result(SecurityEventResult value) { result = value; return this; }
        public Builder reasonCode(String value) { reasonCode = value; return this; }
        public Builder resourceType(String value) { resourceType = value; return this; }
        public Builder resourceId(String value) { resourceId = value; return this; }
        public Builder orgScope(String value) { orgScope = value; return this; }
        public Builder dataCount(long value) { dataCount = value; return this; }
        public Builder latencyMs(long value) { latencyMs = value; return this; }
        public Builder attributes(Map<String, String> value) { attributes = value == null ? new LinkedHashMap<String, String>() : value; return this; }
        /** @return the immutable event represented by this builder */
        public SecurityEvent build() { return new SecurityEvent(this); }
    }
}

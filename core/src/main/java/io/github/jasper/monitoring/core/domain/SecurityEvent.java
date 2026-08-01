package io.github.jasper.monitoring.core.domain;


import io.github.jasper.monitoring.api.AccountType;
import io.github.jasper.monitoring.api.EventInputIssue;
import io.github.jasper.monitoring.api.EventInputStatus;
import io.github.jasper.monitoring.api.EventInputValidation;
import io.github.jasper.monitoring.api.SecurityEventDraft;
import io.github.jasper.monitoring.api.SecurityEventResult;
import io.github.jasper.monitoring.api.SecurityEventType;
import io.github.jasper.monitoring.api.error.MonitoringErrorCode;
import io.github.jasper.monitoring.api.error.MonitoringValidationException;
import io.github.jasper.monitoring.api.error.MonitoringStateException;
import io.github.jasper.monitoring.api.fact.FactDefinition;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Optional;

/**
 * 用于检测和审计的不可变服务端安全事件。
 *
 * <p>事件属性必须已经通过 API 层的敏感数据与日志安全校验。该对象表示接收后的可信快照，
 * 不应由宿主业务代码直接修改或复用为客户端请求模型。</p>
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
    private final boolean dataCountKnown;
    private final long latencyMs;
    private final boolean latencyMsKnown;
    private final EventInputStatus inputStatus;
    private final List<EventInputIssue> inputIssues;
    private final Map<String, String> attributes;
    private final List<EventFact> facts;

    private SecurityEvent(Builder builder, EventInputValidation validation) {
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
        this.dataCountKnown = builder.dataCountKnown;
        this.latencyMs = builder.latencyMs;
        this.latencyMsKnown = builder.latencyMsKnown;
        this.inputStatus = validation.getStatus();
        this.inputIssues = Collections.unmodifiableList(new ArrayList<EventInputIssue>(validation.getIssues()));
        this.attributes = Collections.unmodifiableMap(new LinkedHashMap<String, String>(builder.attributes));
        this.facts = Collections.unmodifiableList(new ArrayList<EventFact>(builder.facts));
    }

    /**
     * 将已校验的宿主事件草稿转换为可持久化事件。
     *
     * @param draft 来自可信服务端接入点的已校验输入
     * @param systemId 事件来源系统的配置标识
     * @param eventId 服务端生成的不可变事件标识
     * @param receivedAt 服务端接收事件时加盖的时间
     * @return 可用于持久化与规则评估的不可变事件
     */
    public static SecurityEvent from(SecurityEventDraft draft, String systemId, String eventId, Instant receivedAt) {
        return from(draft, systemId, eventId, receivedAt, EventInputValidation.valid());
    }

    /**
     * 将已校验的宿主事件草稿和其输入质量结论转换为可持久化事件。
     *
     * @param draft 来自可信服务端接入点的已校验输入
     * @param systemId 事件来源系统的配置标识
     * @param eventId 服务端生成的不可变事件标识
     * @param receivedAt 服务端接收事件时加盖的时间
     * @param validation 规则输入质量结论
     * @return 可用于持久化与规则评估的不可变事件
     */
    public static SecurityEvent from(SecurityEventDraft draft, String systemId, String eventId, Instant receivedAt,
                                     EventInputValidation validation) {
        if (validation == null) {
            throw new MonitoringValidationException(MonitoringErrorCode.REQUIRED_FIELD_MISSING,
                "validation is required");
        }
        return builder()
            .eventId(eventId).systemId(systemId).eventType(draft.getEventType())
            .occurredAt(draft.getOccurredAt()).receivedAt(receivedAt)
            .userId(draft.getUserId()).accountType(draft.getAccountType()).roleIds(draft.getRoleIds())
            .sourceIp(draft.getSourceIp()).deviceIdHash(draft.getDeviceIdHash()).sessionIdHash(draft.getSessionIdHash())
            .requestId(draft.getRequestId()).traceId(draft.getTraceId()).action(draft.getAction()).result(draft.getResult())
            .reasonCode(draft.getReasonCode()).resourceType(draft.getResourceType()).resourceId(draft.getResourceId())
            .orgScope(draft.getOrgScope()).dataCount(draft.getDataCount()).dataCountKnown(draft.hasDataCount())
            .latencyMs(draft.getLatencyMs()).latencyMsKnown(draft.hasLatencyMs())
            .inputStatus(validation.getStatus()).inputIssues(validation.getIssues())
            .attributes(draft.getAttributes()).build();
    }

    /** @return 主要供仓储适配器重建已持久化事件使用的构建器 */
    public static Builder builder() { return new Builder(); }
    /** @return 服务端生成的不可变事件标识 */
    public String getEventId() { return eventId; }
    /** @return 配置的来源系统标识 */
    public String getSystemId() { return systemId; }
    /** @return 标准化安全事件类型 */
    public SecurityEventType getEventType() { return eventType; }
    /** @return 业务动作实际发生的服务端时间 */
    public Instant getOccurredAt() { return occurredAt; }
    /** @return 监测器接收事件的服务端时间 */
    public Instant getReceivedAt() { return receivedAt; }
    /** @return 可信用户标识；匿名事件时可为 {@code null} */
    public String getUserId() { return userId; }
    /** @return 可信账户类型 */
    public AccountType getAccountType() { return accountType; }
    /** @return 已标准化的角色标识集合；返回只读集合 */
    public Set<String> getRoleIds() { return roleIds; }
    /** @return 经可信代理解析后的来源 IP 地址 */
    public String getSourceIp() { return sourceIp; }
    /** @return 已哈希的设备标识；不可保存设备原文 */
    public String getDeviceIdHash() { return deviceIdHash; }
    /** @return 已哈希的会话标识；不可保存会话原文 */
    public String getSessionIdHash() { return sessionIdHash; }
    /** @return 宿主请求标识；未提供时可为 {@code null} */
    public String getRequestId() { return requestId; }
    /** @return 与宿主日志链路关联的追踪标识；未提供时可为 {@code null} */
    public String getTraceId() { return traceId; }
    /** @return 稳定的服务端业务动作名称 */
    public String getAction() { return action; }
    /** @return 动作处理结果 */
    public SecurityEventResult getResult() { return result; }
    /** @return 不包含敏感信息的标准化原因码；未提供时可为 {@code null} */
    public String getReasonCode() { return reasonCode; }
    /** @return 资源类型；无具体资源时可为 {@code null} */
    public String getResourceType() { return resourceType; }
    /** @return 资源标识；不得写入资源敏感原文 */
    public String getResourceId() { return resourceId; }
    /** @return 宿主组织或租户范围；未提供时可为 {@code null} */
    public String getOrgScope() { return orgScope; }
    /** @return 业务动作涉及的数据量，未统计时为零 */
    public long getDataCount() { return dataCount; }
    /** @return 是否由宿主明确提供数据量；显式 {@code 0} 仍返回 {@code true} */
    public boolean hasDataCount() { return dataCountKnown; }
    /** @return 业务动作服务端耗时（毫秒），未统计时为零 */
    public long getLatencyMs() { return latencyMs; }
    /** @return 是否由宿主明确提供服务端耗时；显式 {@code 0} 仍返回 {@code true} */
    public boolean hasLatencyMs() { return latencyMsKnown; }
    /** @return 事件接收时的规则输入质量状态 */
    public EventInputStatus getInputStatus() { return inputStatus; }
    /** @return 不可变的稳定输入问题集合 */
    public List<EventInputIssue> getInputIssues() { return inputIssues; }
    /** @return 已校验的补充属性；返回只读映射 */
    public Map<String, String> getAttributes() { return attributes; }
    /** @return 与该事件关联的不可变事实快照列表 */
    public List<EventFact> getFacts() { return facts; }

    /** Decodes a persisted fact through the authoritative typed definition. */
    public <T> Optional<T> getFact(FactDefinition<T> definition) {
        java.util.Objects.requireNonNull(definition, "definition");
        for (EventFact fact : facts) {
            if (!definition.getKey().equals(fact.getKey())) {
                continue;
            }
            if (!definition.getValueType().getName().equals(fact.getValueType())) {
                throw new MonitoringStateException(MonitoringErrorCode.INVALID_FIELD_VALUE,
                    "Persisted fact type does not match definition: " + definition.getKey());
            }
            try {
                return Optional.of(definition.decode(fact.getValueText()));
            } catch (RuntimeException invalidValue) {
                throw new MonitoringStateException(MonitoringErrorCode.INVALID_FIELD_VALUE,
                    "Persisted fact value is invalid: " + definition.getKey(), invalidValue);
            }
        }
        return Optional.empty();
    }
    /**
     * @param key 已标准化的属性键
     * @return 对应属性值；不存在时为 {@code null}
     */
    public String getAttribute(String key) { return attributes.get(key); }

    /** @return 优先使用用户标识；匿名事件则使用来源 IP 作为规则关联主体 */
    public String subject() {
        return userId == null || userId.isEmpty() ? sourceIp : userId;
    }

    /** 用于从可信数据源重建不可变安全事件的可变构建器。 */
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
        private boolean dataCountKnown;
        private long latencyMs;
        private boolean latencyMsKnown;
        private EventInputStatus inputStatus = EventInputStatus.UNKNOWN;
        private List<EventInputIssue> inputIssues = new ArrayList<EventInputIssue>();
        private Map<String, String> attributes = new LinkedHashMap<String, String>();
        private List<EventFact> facts = new ArrayList<EventFact>();
        /** @param value 服务端生成的事件标识 @return 当前构建器 */
        public Builder eventId(String value) { eventId = value; return this; }
        /** @param value 事件来源系统标识 @return 当前构建器 */
        public Builder systemId(String value) { systemId = value; return this; }
        /** @param value 标准化安全事件类型 @return 当前构建器 */
        public Builder eventType(SecurityEventType value) { eventType = value; return this; }
        /** @param value 业务动作发生时间 @return 当前构建器 */
        public Builder occurredAt(Instant value) { occurredAt = value; return this; }
        /** @param value 监测器接收事件时间 @return 当前构建器 */
        public Builder receivedAt(Instant value) { receivedAt = value; return this; }
        /** @param value 可信用户标识 @return 当前构建器 */
        public Builder userId(String value) { userId = value; return this; }
        /** @param value 账户类型 @return 当前构建器 */
        public Builder accountType(AccountType value) { accountType = value; return this; }
        /** @param value 已标准化角色标识集合 @return 当前构建器 */
        public Builder roleIds(Set<String> value) { roleIds = value == null ? new LinkedHashSet<String>() : value; return this; }
        /** @param value 经可信代理解析的来源 IP @return 当前构建器 */
        public Builder sourceIp(String value) { sourceIp = value; return this; }
        /** @param value 已哈希的设备标识 @return 当前构建器 */
        public Builder deviceIdHash(String value) { deviceIdHash = value; return this; }
        /** @param value 已哈希的会话标识 @return 当前构建器 */
        public Builder sessionIdHash(String value) { sessionIdHash = value; return this; }
        /** @param value 宿主请求标识 @return 当前构建器 */
        public Builder requestId(String value) { requestId = value; return this; }
        /** @param value 宿主日志链路追踪标识 @return 当前构建器 */
        public Builder traceId(String value) { traceId = value; return this; }
        /** @param value 稳定服务端动作名称 @return 当前构建器 */
        public Builder action(String value) { action = value; return this; }
        /** @param value 服务端动作处理结果 @return 当前构建器 */
        public Builder result(SecurityEventResult value) { result = value; return this; }
        /** @param value 不含敏感信息的标准化原因码 @return 当前构建器 */
        public Builder reasonCode(String value) { reasonCode = value; return this; }
        /** @param value 资源类型 @return 当前构建器 */
        public Builder resourceType(String value) { resourceType = value; return this; }
        /** @param value 经脱敏或标准化的资源标识 @return 当前构建器 */
        public Builder resourceId(String value) { resourceId = value; return this; }
        /** @param value 组织或租户范围 @return 当前构建器 */
        public Builder orgScope(String value) { orgScope = value; return this; }
        /** @param value 涉及数据量 @return 当前构建器 */
        public Builder dataCount(long value) { dataCount = value; dataCountKnown = true; return this; }
        /** @param value 是否明确提供了涉及数据量 @return 当前构建器 */
        public Builder dataCountKnown(boolean value) { dataCountKnown = value; return this; }
        /** @param value 服务端耗时（毫秒） @return 当前构建器 */
        public Builder latencyMs(long value) { latencyMs = value; latencyMsKnown = true; return this; }
        /** @param value 是否明确提供了服务端耗时 @return 当前构建器 */
        public Builder latencyMsKnown(boolean value) { latencyMsKnown = value; return this; }
        /** @param value 规则输入质量状态 @return 当前构建器 */
        public Builder inputStatus(EventInputStatus value) {
            inputStatus = value == null ? EventInputStatus.UNKNOWN : value;
            return this;
        }
        /** @param value 稳定输入问题集合 @return 当前构建器 */
        public Builder inputIssues(Collection<EventInputIssue> value) {
            inputIssues = value == null ? new ArrayList<EventInputIssue>() : new ArrayList<EventInputIssue>(value);
            return this;
        }
        /** @param value 已校验补充属性 @return 当前构建器 */
        public Builder attributes(Map<String, String> value) { attributes = value == null ? new LinkedHashMap<String, String>() : value; return this; }
        /** @param value 已校验的事实快照集合 @return 当前构建器 */
        public Builder facts(Collection<EventFact> value) {
            facts = value == null ? new ArrayList<EventFact>() : new ArrayList<EventFact>(value);
            return this;
        }
        /** @return 本构建器表示的不可变安全事件 */
        public SecurityEvent build() {
            EventInputValidation validation = EventInputValidation.of(inputStatus, inputIssues,
                ineligibleRuleIds(inputIssues));
            return new SecurityEvent(this, validation);
        }

        private static Set<String> ineligibleRuleIds(Collection<EventInputIssue> issues) {
            Set<String> ruleIds = new LinkedHashSet<String>();
            for (EventInputIssue issue : issues) {
                if (issue != null) {
                    ruleIds.add(issue.getRuleId());
                }
            }
            return ruleIds;
        }
    }
}

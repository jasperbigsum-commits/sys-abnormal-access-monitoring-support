package io.github.jasper.monitoring.api;

import io.github.jasper.monitoring.api.error.MonitoringErrorCode;
import io.github.jasper.monitoring.api.error.MonitoringValidationException;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 宿主系统集成方向监测组件提供的、不可变且已清洗的安全事件草稿。
 *
 * <p>监测组件会分配持久化标识，并根据可信服务端事实评估该草稿。此模型中不得存放凭据、
 * 原始会话标识、浏览器会话数据（Cookie）、请求体或其他敏感载荷；应使用单向散列值和稳定原因码替代。</p>
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
    private final boolean dataCountKnown;
    private final long latencyMs;
    private final boolean latencyMsKnown;
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
        this.dataCountKnown = builder.dataCount != null;
        this.dataCount = nonNegative(builder.dataCount == null ? 0L : builder.dataCount.longValue(), "dataCount");
        this.latencyMsKnown = builder.latencyMs != null;
        this.latencyMs = nonNegative(builder.latencyMs == null ? 0L : builder.latencyMs.longValue(), "latencyMs");
        this.occurredAt = required(builder.occurredAt, "occurredAt");
        this.reasonCode = SecurityFieldSanitizer.text(builder.reasonCode, 128);
        this.attributes = immutableAttributes(builder.attributes);
    }

    /**
     * 创建新的事件草稿。
     *
     * @return 在调用 {@link Builder#build()} 时校验并清洗字段的构建器（Builder）
     */
    public static Builder builder() {
        return new Builder();
    }

    /** @return 标准化安全活动类别 */
    public SecurityEventType getEventType() { return eventType; }
    /** @return 已清洗的操作名称 */
    public String getAction() { return action; }
    /** @return 最终服务端操作结果 */
    public SecurityEventResult getResult() { return result; }
    /** @return 可信客户端网络地址 */
    public String getSourceIp() { return sourceIp; }
    /** @return 请求关联标识 */
    public String getRequestId() { return requestId; }
    /** @return 分布式追踪标识；不可用时为 {@code null} */
    public String getTraceId() { return traceId; }
    /** @return 由服务端解析的用户标识；匿名时为 {@code null} */
    public String getUserId() { return userId; }
    /** @return 与事件关联的账号类型 */
    public AccountType getAccountType() { return accountType; }
    /** @return 不可变的宿主角色标识集合 */
    public Set<String> getRoleIds() { return roleIds; }
    /** @return 单向散列后的会话标识；不可用时为 {@code null} */
    public String getSessionIdHash() { return sessionIdHash; }
    /** @return 单向散列后的设备标识；不可用时为 {@code null} */
    public String getDeviceIdHash() { return deviceIdHash; }
    /** @return 逻辑资源类别；不可用时为 {@code null} */
    public String getResourceType() { return resourceType; }
    /** @return 资源标识；不可用时为 {@code null} */
    public String getResourceId() { return resourceId; }
    /** @return 租户、组织或数据域边界；不可用时为 {@code null} */
    public String getOrgScope() { return orgScope; }
    /** @return 受影响记录数；未提供时为 {@code 0}，用于兼容既有调用方 */
    public long getDataCount() { return dataCount; }
    /** @return 是否由宿主明确提供受影响记录数；显式 {@code 0} 仍返回 {@code true} */
    public boolean hasDataCount() { return dataCountKnown; }
    /** @return 实测操作耗时，单位为毫秒；未提供时为 {@code 0}，用于兼容既有调用方 */
    public long getLatencyMs() { return latencyMs; }
    /** @return 是否由宿主明确提供实测操作耗时；显式 {@code 0} 仍返回 {@code true} */
    public boolean hasLatencyMs() { return latencyMsKnown; }
    /** @return 服务端观测到的事件时间 */
    public Instant getOccurredAt() { return occurredAt; }
    /** @return 稳定、非敏感的原因码；不可用时为 {@code null} */
    public String getReasonCode() { return reasonCode; }
    /** @return 不可变且已清洗的非敏感扩展属性 */
    public Map<String, String> getAttributes() { return attributes; }
    /**
     * 获取一个非敏感扩展属性。
     *
     * @param key 属性名称
     * @return 已清洗的属性值；不存在时为 {@code null}
     */
    public String getAttribute(String key) {
        String normalized = SecurityFieldSanitizer.text(key, 128);
        return normalized == null || normalized.isEmpty() ? null : attributes.get(normalized.toLowerCase(Locale.ROOT));
    }

    private static long nonNegative(long value, String name) {
        if (value < 0) {
            throw new MonitoringValidationException(MonitoringErrorCode.INVALID_FIELD_VALUE,
                name + " must not be negative");
        }
        return value;
    }

    private static String requiredText(String value, String name, int maximumLength) {
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
        Set<String> normalizedKeys = new LinkedHashSet<String>();
        if (attributes != null) {
            for (Map.Entry<String, String> entry : attributes.entrySet()) {
                String key = SecurityFieldSanitizer.normalizeAttributeKey(entry.getKey());
                if (!normalizedKeys.add(key)) {
                    throw new MonitoringValidationException(MonitoringErrorCode.INVALID_FIELD_VALUE,
                        "Duplicate event attribute key");
                }
                String value = SecurityFieldSanitizer.text(entry.getValue(), 512);
                if (value != null) {
                    sanitized.put(key, value);
                }
            }
        }
        return Collections.unmodifiableMap(sanitized);
    }

    /** {@link SecurityEventDraft} 的构建器（Builder）。 */
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
        private Long dataCount;
        private Long latencyMs;
        private Instant occurredAt;
        private String reasonCode;
        private Map<String, String> attributes = new LinkedHashMap<String, String>();

        /**
         * 设置标准化安全活动类别。
         *
         * @param value 标准化安全活动类别
         * @return 当前构建器
         */
        public Builder eventType(SecurityEventType value) { this.eventType = value; return this; }
        /**
         * 设置服务端操作名称。
         *
         * @param value 服务端操作名称
         * @return 当前构建器
         */
        public Builder action(String value) { this.action = value; return this; }
        /**
         * 设置最终服务端操作结果。
         *
         * @param value 最终服务端操作结果
         * @return 当前构建器
         */
        public Builder result(SecurityEventResult value) { this.result = value; return this; }
        /**
         * 设置可信客户端网络地址。
         *
         * @param value 可信客户端网络地址（IP）
         * @return 当前构建器
         */
        public Builder sourceIp(String value) { this.sourceIp = value; return this; }
        /**
         * 设置请求关联标识。
         *
         * @param value 请求关联标识
         * @return 当前构建器
         */
        public Builder requestId(String value) { this.requestId = value; return this; }
        /**
         * 设置分布式追踪标识。
         *
         * @param value 分布式追踪标识
         * @return 当前构建器
         */
        public Builder traceId(String value) { this.traceId = value; return this; }
        /**
         * 设置由服务端解析的用户标识。
         *
         * @param value 由服务端解析的用户标识
         * @return 当前构建器
         */
        public Builder userId(String value) { this.userId = value; return this; }
        /**
         * 设置主体类型。
         *
         * @param value 主体类型
         * @return 当前构建器
         */
        public Builder accountType(AccountType value) { this.accountType = value; return this; }
        /**
         * 添加一个宿主角色标识。
         *
         * @param value 要添加的宿主角色标识
         * @return 当前构建器
         */
        public Builder roleId(String value) { if (value != null) { this.roleIds.add(value); } return this; }
        /**
         * 使用防御性副本替换角色标识集合。
         *
         * @param value 宿主角色标识；构建器会复制该集合
         * @return 当前构建器
         */
        public Builder roleIds(Set<String> value) { this.roleIds = value == null ? new LinkedHashSet<String>() : new LinkedHashSet<String>(value); return this; }
        /**
         * 设置单向散列后的会话标识。
         *
         * @param value 单向散列后的会话标识
         * @return 当前构建器
         */
        public Builder sessionIdHash(String value) { this.sessionIdHash = value; return this; }
        /**
         * 设置单向散列后的设备标识。
         *
         * @param value 单向散列后的设备标识
         * @return 当前构建器
         */
        public Builder deviceIdHash(String value) { this.deviceIdHash = value; return this; }
        /**
         * 设置逻辑资源类别。
         *
         * @param value 逻辑资源类别
         * @return 当前构建器
         */
        public Builder resourceType(String value) { this.resourceType = value; return this; }
        /**
         * 设置宿主资源标识。
         *
         * @param value 宿主资源标识
         * @return 当前构建器
         */
        public Builder resourceId(String value) { this.resourceId = value; return this; }
        /**
         * 设置租户、组织或数据域边界。
         *
         * @param value 租户、组织或数据域边界
         * @return 当前构建器
         */
        public Builder orgScope(String value) { this.orgScope = value; return this; }
        /**
         * 设置受影响记录数。
         *
         * @param value 受影响记录数，不能为负数；调用该方法即使传入 {@code 0} 也会标记为已提供
         * @return 当前构建器
         */
        public Builder dataCount(long value) { this.dataCount = Long.valueOf(value); return this; }
        /**
         * 设置实测操作耗时。
         *
         * @param value 实测操作耗时，单位为毫秒，不能为负数；调用该方法即使传入 {@code 0} 也会标记为已提供
         * @return 当前构建器
         */
        public Builder latencyMs(long value) { this.latencyMs = Long.valueOf(value); return this; }
        /**
         * 设置服务端观测到的事件时间。
         *
         * @param value 服务端观测到的事件时间
         * @return 当前构建器
         */
        public Builder occurredAt(Instant value) { this.occurredAt = value; return this; }
        /**
         * 设置稳定、非敏感的原因码。
         *
         * @param value 稳定、非敏感的原因码
         * @return 当前构建器
         */
        public Builder reasonCode(String value) { this.reasonCode = value; return this; }
        /**
         * 添加经批准的非敏感属性。
         *
         * @param key 属性键；构建时会拒绝指向凭据类信息的键
         * @param value 属性值
         * @return 当前构建器
         */
        public Builder attribute(String key, String value) {
            String normalizedKey = SecurityFieldSanitizer.normalizeAttributeKey(key);
            if (this.attributes.containsKey(normalizedKey)) {
                throw new MonitoringValidationException(MonitoringErrorCode.INVALID_FIELD_VALUE,
                    "Duplicate event attribute key");
            }
            this.attributes.put(normalizedKey, value);
            return this;
        }
        /**
         * 使用复制后的映射替换扩展属性。
         *
         * @param value 非敏感属性；传入 {@code null} 时使用空映射
         * @return 当前构建器
         */
        public Builder attributes(Map<String, String> value) { this.attributes = value == null ? new LinkedHashMap<String, String>() : new LinkedHashMap<String, String>(value); return this; }
        /**
         * 校验必需字段并返回不可变、已清洗的事件草稿。
         *
         * @return 可直接交给监测组件处理的事件草稿
         * @throws IllegalArgumentException 当缺少必需字段、数量为负数或属性不安全时抛出
         */
        public SecurityEventDraft build() { return new SecurityEventDraft(this); }
    }
}

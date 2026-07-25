package io.github.jasper.monitoring.api;

import io.github.jasper.monitoring.api.error.MonitoringErrorCode;
import io.github.jasper.monitoring.api.error.MonitoringValidationException;
import java.lang.reflect.AnnotatedElement;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * {@link MonitorAction} 的框架无关静态定义，用于将注解埋点与注册式埋点使用同一套元数据。
 *
 * <p>该对象只保存可在启动期确定的动作信息，不保存用户、网络地址（IP）、资源标识（ID）、数据量或其他运行时事实。
 * 手工埋点应先注册本定义，再由记录器预填充可信请求与身份上下文。</p>
 */
public final class MonitorActionDefinition {
    private static final Pattern RULE_TAG = Pattern.compile("[a-z0-9][a-z0-9_.-]{0,63}");
    private static final String RULE_TAG_PREFIX = "monitor.rule-tag.";

    private final String action;
    private final SecurityEventType eventType;
    private final String resourceType;
    private final Set<String> ruleTags;
    private final Map<String, String> attributes;

    private MonitorActionDefinition(Builder builder) {
        this.action = requiredAction(builder.action);
        this.eventType = builder.eventType == null ? SecurityEventType.QUERY : builder.eventType;
        this.resourceType = SecurityFieldSanitizer.text(builder.resourceType, 128);
        this.ruleTags = immutableRuleTags(builder.ruleTags);
        this.attributes = immutableAttributes(builder.attributes);
    }

    /**
     * 创建指定动作编码的定义构建器。
     *
     * @param action 宿主定义的稳定动作编码
     * @return 用于补充静态动作元数据的构建器
     */
    public static Builder builder(String action) {
        return new Builder(action);
    }

    /**
     * 从 {@link MonitorAction} 统一解析静态动作定义。
     *
     * @param annotation 控制器（Controller）类型或方法上的监测注解
     * @return 与注解等价的不可变动作定义
     * @throws IllegalArgumentException 当动作编码缺失、两个编码不一致或规则标记不合法时抛出
     */
    public static MonitorActionDefinition from(MonitorAction annotation) {
        Objects.requireNonNull(annotation, "annotation");
        String shorthand = normalized(annotation.value());
        String named = normalized(annotation.action());
        if (shorthand != null && named != null && !shorthand.equals(named)) {
            throw invalid("MonitorAction value and action must match when both are set");
        }
        return builder(shorthand == null ? named : shorthand)
            .eventType(annotation.eventType())
            .resourceType(annotation.resourceType())
            .ruleTags(annotation.ruleTags())
            .build();
    }

    /**
     * Resolves an annotated type or method and its static action attributes.
     *
     * <p>This overload intentionally reads attributes only from the supplied element. A caller
     * applying method-over-type precedence must pass the selected element, so a type default does
     * not leak into a separately annotated method.</p>
     *
     * @param element annotated type or method
     * @return action definition with immutable static attributes
     * @throws IllegalArgumentException if no action is declared or an attribute declaration is invalid
     */
    public static MonitorActionDefinition from(AnnotatedElement element) {
        Objects.requireNonNull(element, "element");
        MonitorAction action = element.getAnnotation(MonitorAction.class);
        if (action == null) {
            throw required("MonitorAction is required");
        }
        Builder builder = from(action).toBuilder();
        for (MonitorActionAttribute attribute : element.getAnnotationsByType(MonitorActionAttribute.class)) {
            validateStaticAttribute(attribute);
            builder.attribute(attribute.name(), attribute.value());
        }
        return builder.build();
    }

    /**
     * Validates an attribute declared on an action type or method.
     *
     * @param attribute static declaration to validate
     * @throws IllegalArgumentException if the declaration attempts dynamic binding or omits a static key or value
     */
    public static void validateStaticAttribute(MonitorActionAttribute attribute) {
        Objects.requireNonNull(attribute, "attribute");
        if (attribute.target() != MonitorActionAttributeTarget.ATTRIBUTE || !attribute.path().isEmpty()) {
            throw invalid("Type and method MonitorActionAttribute declarations are static attributes only");
        }
        if (!hasText(attribute.name()) || !hasText(attribute.value())) {
            throw required("Static MonitorActionAttribute name and value are required");
        }
    }

    /**
     * Validates a fact declaration on a method parameter before an adapter resolves its path.
     *
     * @param attribute parameter declaration to validate
     * @throws IllegalArgumentException if a static value is supplied or an attribute target has no name
     */
    public static void validateParameterAttribute(MonitorActionAttribute attribute) {
        Objects.requireNonNull(attribute, "attribute");
        if (!attribute.value().isEmpty()) {
            throw invalid("Parameter MonitorActionAttribute declarations must not define value");
        }
        if (attribute.target() == MonitorActionAttributeTarget.ATTRIBUTE && !hasText(attribute.name())) {
            throw required("Parameter attribute declarations require a name");
        }
    }

    /**
     * 将一个规则标记转换为事件属性键。
     *
     * @param ruleTag 已定义的静态规则标记
     * @return 规则可读取的统一属性键
     */
    public static String ruleTagAttributeKey(String ruleTag) {
        return RULE_TAG_PREFIX + normalizeRuleTag(ruleTag);
    }

    /** @return 宿主内唯一且稳定的业务动作编码 */
    public String getAction() {
        return action;
    }

    /** @return 通用安全事件类别；未显式指定时为 {@link SecurityEventType#QUERY} */
    public SecurityEventType getEventType() {
        return eventType;
    }

    /** @return 静态逻辑资源类别；未定义时为 {@code null} */
    public String getResourceType() {
        return resourceType;
    }

    /** @return 不可变的静态规则标记集合 */
    public Set<String> getRuleTags() {
        return ruleTags;
    }

    /** @return immutable static non-sensitive attributes */
    public Map<String, String> getAttributes() {
        return attributes;
    }

    /**
     * Creates a builder initialized from this immutable definition.
     *
     * @return initialized mutable builder
     */
    public Builder toBuilder() {
        Builder builder = builder(action).eventType(eventType).resourceType(resourceType);
        builder.ruleTags(ruleTags.toArray(new String[ruleTags.size()]));
        for (Map.Entry<String, String> attribute : attributes.entrySet()) {
            builder.attribute(attribute.getKey(), attribute.getValue());
        }
        return builder;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof MonitorActionDefinition)) {
            return false;
        }
        MonitorActionDefinition that = (MonitorActionDefinition) other;
        return action.equals(that.action) && eventType == that.eventType
            && Objects.equals(resourceType, that.resourceType) && ruleTags.equals(that.ruleTags)
            && attributes.equals(that.attributes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(action, eventType, resourceType, ruleTags, attributes);
    }

    /** 用于创建不可变动作定义的构建器。 */
    public static final class Builder {
        private final String action;
        private SecurityEventType eventType = SecurityEventType.QUERY;
        private String resourceType;
        private final Set<String> ruleTags = new LinkedHashSet<String>();
        private final Map<String, String> attributes = new LinkedHashMap<String, String>();

        private Builder(String action) {
            this.action = action;
        }

        /**
         * 设置通用安全事件类别。
         *
         * @param value 通用安全事件类别
         * @return 当前构建器
         */
        public Builder eventType(SecurityEventType value) {
            this.eventType = value;
            return this;
        }

        /**
         * 设置静态逻辑资源类别。
         *
         * @param value 静态资源类别
         * @return 当前构建器
         */
        public Builder resourceType(String value) {
            this.resourceType = value;
            return this;
        }

        /**
         * 添加一个规则选择标记。
         *
         * @param value 小写规则标记
         * @return 当前构建器
         */
        public Builder ruleTag(String value) {
            this.ruleTags.add(normalizeRuleTag(value));
            return this;
        }

        /**
         * 替换规则选择标记。
         *
         * @param values 静态规则标记；允许为空数组
         * @return 当前构建器
         */
        public Builder ruleTags(String... values) {
            this.ruleTags.clear();
            if (values != null) {
                for (String value : values) {
                    this.ruleTags.add(normalizeRuleTag(value));
                }
            }
            return this;
        }

        /**
         * Adds a static, non-sensitive action attribute.
         *
         * @param key static attribute key; reserved rule-tag keys are not allowed
         * @param value non-empty static attribute value
         * @return current builder
         */
        public Builder attribute(String key, String value) {
            String safeKey = SecurityFieldSanitizer.normalizeAttributeKey(key);
            if (safeKey.toLowerCase(Locale.ROOT).startsWith(RULE_TAG_PREFIX)) {
                throw invalid("MonitorAction static attributes cannot use the reserved rule-tag namespace");
            }
            if (attributes.containsKey(safeKey)) {
                throw invalid("Duplicate MonitorAction static attribute key");
            }
            String safeValue = SecurityFieldSanitizer.text(value, 512);
            if (safeValue == null || safeValue.isEmpty()) {
                throw required("MonitorAction static attribute value is required");
            }
            attributes.put(safeKey, safeValue);
            return this;
        }

        /**
         * 校验并生成不可变定义。
         *
         * @return 可注册、可复用的动作定义
         */
        public MonitorActionDefinition build() {
            return new MonitorActionDefinition(this);
        }
    }

    private static String requiredAction(String value) {
        String normalized = normalized(value);
        if (normalized == null) {
            throw required("MonitorAction action is required");
        }
        return normalized;
    }

    private static String normalized(String value) {
        String normalized = SecurityFieldSanitizer.text(value, 128);
        return normalized == null || normalized.isEmpty() ? null : normalized;
    }

    private static boolean hasText(String value) {
        String normalized = SecurityFieldSanitizer.text(value, 512);
        return normalized != null && !normalized.isEmpty();
    }

    private static Set<String> immutableRuleTags(Set<String> values) {
        Set<String> normalized = new LinkedHashSet<String>();
        for (String value : values) {
            normalized.add(normalizeRuleTag(value));
        }
        return Collections.unmodifiableSet(normalized);
    }

    private static Map<String, String> immutableAttributes(Map<String, String> values) {
        Map<String, String> normalized = new LinkedHashMap<String, String>();
        for (Map.Entry<String, String> entry : values.entrySet()) {
            String key = SecurityFieldSanitizer.normalizeAttributeKey(entry.getKey());
            if (key.toLowerCase(Locale.ROOT).startsWith(RULE_TAG_PREFIX)) {
                throw invalid("MonitorAction static attributes cannot use the reserved rule-tag namespace");
            }
            if (normalized.containsKey(key)) {
                throw invalid("Duplicate MonitorAction static attribute key");
            }
            String value = SecurityFieldSanitizer.text(entry.getValue(), 512);
            if (value == null || value.isEmpty()) {
                throw required("MonitorAction static attribute value is required");
            }
            normalized.put(key, value);
        }
        return Collections.unmodifiableMap(normalized);
    }

    private static String normalizeRuleTag(String value) {
        String normalized = SecurityFieldSanitizer.text(value, 64);
        if (normalized == null || !RULE_TAG.matcher(normalized.toLowerCase(Locale.ROOT)).matches()) {
            throw invalid("MonitorAction rule tag must match [a-z0-9][a-z0-9_.-]{0,63}");
        }
        return normalized.toLowerCase(Locale.ROOT);
    }

    private static MonitoringValidationException required(String message) {
        return new MonitoringValidationException(MonitoringErrorCode.REQUIRED_FIELD_MISSING, message);
    }

    private static MonitoringValidationException invalid(String message) {
        return new MonitoringValidationException(MonitoringErrorCode.INVALID_FIELD_VALUE, message);
    }
}

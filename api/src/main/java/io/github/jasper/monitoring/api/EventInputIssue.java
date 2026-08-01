package io.github.jasper.monitoring.api;

import io.github.jasper.monitoring.api.error.MonitoringErrorCode;
import io.github.jasper.monitoring.api.error.MonitoringValidationException;
import java.util.regex.Pattern;

/**
 * 一个稳定且不含原始输入值的监测事实问题。
 *
 * <p>该类型刻意只保留规则、事实、问题码和来源类别，避免把参数值、异常消息或其他敏感诊断
 * 写入事件。</p>
 */
public final class EventInputIssue {
    private static final int MAXIMUM_IDENTIFIER_LENGTH = 128;
    private static final Pattern RULE_ID = Pattern.compile("[A-Za-z][A-Za-z0-9]*(?:[._-][A-Za-z0-9]+)*");
    private static final Pattern FACT_NAME = Pattern.compile("[A-Za-z][A-Za-z0-9_]{0,127}");
    private static final String[] PAYLOAD_FACT_MARKERS = {
        "payload", "body", "rawvalue", "rawinput", "rawdata", "exceptionmessage", "stacktrace"
    };

    private final String ruleId;
    private final String factName;
    private final EventInputIssueCode issueCode;
    private final EventFactSource sourceType;

    private EventInputIssue(String ruleId, String factName, EventInputIssueCode issueCode,
                            EventFactSource sourceType) {
        this.ruleId = requiredRuleId(ruleId);
        this.factName = requiredFactName(factName);
        this.issueCode = requiredIssueCode(issueCode);
        this.sourceType = requiredSourceType(sourceType);
    }

    /**
     * 创建一个仅含稳定诊断标识的问题。
     *
     * @param ruleId 受影响规则的稳定标识
     * @param factName 缺失或无效的事实名称
     * @param issueCode 受控稳定问题码
     * @param sourceType 事实来源类别
     * @return 不包含原始值或异常信息的问题
     */
    public static EventInputIssue of(String ruleId, String factName, EventInputIssueCode issueCode,
                                     EventFactSource sourceType) {
        return new EventInputIssue(ruleId, factName, issueCode, sourceType);
    }

    /**
     * 创建一个缺失事实的问题，并根据事实名称生成受控问题码。
     *
     * @param ruleId 受影响规则的稳定标识
     * @param factName 缺失事实的稳定名称
     * @param sourceType 事实来源类别
     * @return 使用受控 {@code MISSING_*} 问题码的问题
     */
    public static EventInputIssue missing(String ruleId, String factName, EventFactSource sourceType) {
        String requiredFactName = requiredFactName(factName);
        return new EventInputIssue(ruleId, requiredFactName, missingIssueCode(requiredFactName), sourceType);
    }

    /** @return 受影响规则的稳定标识 */
    public String getRuleId() {
        return ruleId;
    }

    /** @return 缺失或无效事实的稳定名称 */
    public String getFactName() {
        return factName;
    }

    /** @return 不含原始输入的稳定问题码 */
    public String getIssueCode() {
        return issueCode.name();
    }

    /** @return 事实来源类别 */
    public String getSourceType() {
        return sourceType.name();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof EventInputIssue)) {
            return false;
        }
        EventInputIssue that = (EventInputIssue) other;
        return ruleId.equals(that.ruleId)
            && factName.equals(that.factName)
            && issueCode == that.issueCode
            && sourceType == that.sourceType;
    }

    @Override
    public int hashCode() {
        int result = ruleId.hashCode();
        result = 31 * result + factName.hashCode();
        result = 31 * result + issueCode.hashCode();
        result = 31 * result + sourceType.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "EventInputIssue{"
            + "ruleId='" + ruleId + '\''
            + ", factName='" + factName + '\''
            + ", issueCode='" + issueCode.name() + '\''
            + ", sourceType='" + sourceType.name() + '\''
            + '}';
    }

    private static String requiredRuleId(String value) {
        if (!isStableRuleId(value)) {
            throw invalid("ruleId must be a stable rule identifier");
        }
        return value;
    }

    private static String requiredFactName(String value) {
        String factName = requiredIdentifier(value, "factName");
        requireNonSensitiveFactName(factName);
        if (!FACT_NAME.matcher(factName).matches() || containsPayloadMarker(factName)) {
            throw invalid("factName must be a safe identifier");
        }
        return factName;
    }

    private static EventInputIssueCode requiredIssueCode(EventInputIssueCode value) {
        if (value == null) {
            throw required("issueCode is required");
        }
        return value;
    }

    private static EventFactSource requiredSourceType(EventFactSource value) {
        if (value == null) {
            throw required("sourceType is required");
        }
        return value;
    }

    static boolean isStableRuleId(String value) {
        if (!isStableIdentifier(value) || !RULE_ID.matcher(value).matches()) {
            return false;
        }
        try {
            SecurityFieldSanitizer.requireSafeAttributeKey(value);
            return true;
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private static String requiredIdentifier(String value, String name) {
        if (!isStableIdentifier(value)) {
            throw invalid(name + " must be a non-empty stable identifier");
        }
        return value;
    }

    private static boolean isStableIdentifier(String value) {
        if (value == null || value.isEmpty() || value.length() > MAXIMUM_IDENTIFIER_LENGTH) {
            return false;
        }
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (Character.isWhitespace(character) || Character.isSpaceChar(character)
                || Character.isISOControl(character)) {
                return false;
            }
        }
        return true;
    }

    private static void requireNonSensitiveFactName(String factName) {
        try {
            SecurityFieldSanitizer.requireSafeAttributeKey(factName);
        } catch (IllegalArgumentException ignored) {
            throw new MonitoringValidationException(MonitoringErrorCode.UNSAFE_EVENT_ATTRIBUTE,
                "factName must be a non-sensitive identifier");
        }
    }

    private static boolean containsPayloadMarker(String factName) {
        StringBuilder normalized = new StringBuilder(factName.length());
        for (int index = 0; index < factName.length(); index++) {
            char character = factName.charAt(index);
            if (Character.isLetterOrDigit(character)) {
                normalized.append(Character.toLowerCase(character));
            }
        }
        String comparable = normalized.toString();
        for (String marker : PAYLOAD_FACT_MARKERS) {
            if (comparable.contains(marker)) {
                return true;
            }
        }
        return false;
    }

    private static EventInputIssueCode missingIssueCode(String factName) {
        if ("dataCount".equals(factName)) {
            return EventInputIssueCode.MISSING_DATA_COUNT;
        }
        if ("latencyMs".equals(factName)) {
            return EventInputIssueCode.MISSING_LATENCY_MS;
        }
        if ("resourceId".equals(factName)) {
            return EventInputIssueCode.MISSING_RESOURCE_ID;
        }
        if ("orgScope".equals(factName)) {
            return EventInputIssueCode.MISSING_ORG_SCOPE;
        }
        if ("sourceIp".equals(factName)) {
            return EventInputIssueCode.MISSING_SOURCE_IP;
        }
        if ("targetUserId".equals(factName)) {
            return EventInputIssueCode.MISSING_TARGET_USER_ID;
        }
        if ("reasonCode".equals(factName)) {
            return EventInputIssueCode.MISSING_REASON_CODE;
        }
        return EventInputIssueCode.MISSING_FACT;
    }

    private static MonitoringValidationException required(String message) {
        return new MonitoringValidationException(MonitoringErrorCode.REQUIRED_FIELD_MISSING, message);
    }

    private static MonitoringValidationException invalid(String message) {
        return new MonitoringValidationException(MonitoringErrorCode.INVALID_FIELD_VALUE, message);
    }
}

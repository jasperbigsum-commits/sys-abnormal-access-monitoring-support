package io.github.jasper.monitoring.api;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
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
    private static final Pattern ISSUE_CODE = Pattern.compile("[A-Z][A-Z0-9]*(?:_[A-Z0-9]+)*");
    private static final Set<String> SOURCE_TYPES = Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(
        "STATIC_DECLARATION", "TRUSTED_REQUEST", "TRUSTED_IDENTITY", "METHOD_PARAMETER",
        "SERVER_COMPUTED", "AUTHORIZATION", "EVENT_ENRICHER")));
    private static final Set<String> PAYLOAD_FACT_MARKERS = Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(
        "payload", "body", "rawvalue", "rawinput", "rawdata", "exceptionmessage", "stacktrace")));

    private final String ruleId;
    private final String factName;
    private final String issueCode;
    private final String sourceType;

    private EventInputIssue(String ruleId, String factName, String issueCode, String sourceType) {
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
     * @param issueCode 稳定问题码
     * @param sourceType 事实来源类别
     * @return 不包含原始值或异常信息的问题
     */
    public static EventInputIssue of(String ruleId, String factName, String issueCode, String sourceType) {
        return new EventInputIssue(ruleId, factName, issueCode, sourceType);
    }

    /**
     * 创建一个缺失事实的问题，并根据事实名称生成稳定问题码。
     *
     * @param ruleId 受影响规则的稳定标识
     * @param factName 缺失事实的稳定名称
     * @param sourceType 事实来源类别
     * @return 使用 {@code MISSING_*} 问题码的问题
     */
    public static EventInputIssue missing(String ruleId, String factName, String sourceType) {
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
        return issueCode;
    }

    /** @return 事实来源类别 */
    public String getSourceType() {
        return sourceType;
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
            && issueCode.equals(that.issueCode)
            && sourceType.equals(that.sourceType);
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
            + ", issueCode='" + issueCode + '\''
            + ", sourceType='" + sourceType + '\''
            + '}';
    }

    private static String requiredRuleId(String value) {
        if (!isStableRuleId(value)) {
            throw new IllegalArgumentException("ruleId must be a stable rule identifier");
        }
        return value;
    }

    private static String requiredFactName(String value) {
        String factName = requiredIdentifier(value, "factName");
        requireNonSensitiveFactName(factName);
        if (!FACT_NAME.matcher(factName).matches() || containsPayloadMarker(factName)) {
            throw new IllegalArgumentException("factName must be a safe identifier");
        }
        return factName;
    }

    private static String requiredIssueCode(String value) {
        String issueCode = requiredIdentifier(value, "issueCode");
        if (!ISSUE_CODE.matcher(issueCode).matches()) {
            throw new IllegalArgumentException("issueCode must be a stable uppercase code");
        }
        return issueCode;
    }

    private static String requiredSourceType(String value) {
        String sourceType = requiredIdentifier(value, "sourceType");
        if (!SOURCE_TYPES.contains(sourceType)) {
            throw new IllegalArgumentException("sourceType must be a supported source category");
        }
        return sourceType;
    }

    static boolean isStableRuleId(String value) {
        return isStableIdentifier(value) && RULE_ID.matcher(value).matches();
    }

    private static String requiredIdentifier(String value, String name) {
        if (!isStableIdentifier(value)) {
            throw new IllegalArgumentException(name + " must be a non-empty stable identifier");
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
            throw new IllegalArgumentException("factName must be a non-sensitive identifier");
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

    private static String missingIssueCode(String factName) {
        StringBuilder code = new StringBuilder();
        char previous = 0;
        for (int index = 0; index < factName.length(); index++) {
            char current = factName.charAt(index);
            if (Character.isLetterOrDigit(current)) {
                if (Character.isUpperCase(current)
                    && (Character.isLowerCase(previous) || Character.isDigit(previous))) {
                    appendSeparator(code);
                }
                code.append(Character.toUpperCase(current));
            } else {
                appendSeparator(code);
            }
            previous = current;
        }
        while (code.length() > 0 && code.charAt(code.length() - 1) == '_') {
            code.deleteCharAt(code.length() - 1);
        }
        return "MISSING_" + code;
    }

    private static void appendSeparator(StringBuilder code) {
        if (code.length() > 0 && code.charAt(code.length() - 1) != '_') {
            code.append('_');
        }
    }
}

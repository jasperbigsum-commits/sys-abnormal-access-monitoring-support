package io.github.jasper.monitoring.api;

/**
 * 一个稳定且不含原始输入值的监测事实问题。
 *
 * <p>该类型刻意只保留规则、事实、问题码和来源类别，避免把参数值、异常消息或其他敏感诊断
 * 写入事件。</p>
 */
public final class EventInputIssue {
    private final String ruleId;
    private final String factName;
    private final String issueCode;
    private final String sourceType;

    private EventInputIssue(String ruleId, String factName, String issueCode, String sourceType) {
        this.ruleId = requiredText(ruleId, "ruleId");
        this.factName = requiredText(factName, "factName");
        this.issueCode = requiredText(issueCode, "issueCode");
        this.sourceType = requiredText(sourceType, "sourceType");
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
        String requiredFactName = requiredText(factName, "factName");
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

    private static String requiredText(String value, String name) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value.trim();
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

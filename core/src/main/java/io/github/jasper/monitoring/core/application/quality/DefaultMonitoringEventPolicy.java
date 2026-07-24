package io.github.jasper.monitoring.core.application.quality;

import io.github.jasper.monitoring.api.EventFactSource;
import io.github.jasper.monitoring.api.EventInputIssue;
import io.github.jasper.monitoring.api.EventInputIssueCode;
import io.github.jasper.monitoring.api.EventInputValidation;
import io.github.jasper.monitoring.api.MonitoringEventPolicy;
import io.github.jasper.monitoring.api.SecurityEventDraft;
import io.github.jasper.monitoring.api.SecurityEventType;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 基线规则的最小事实契约。
 *
 * <p>策略只排除无法安全评估的内置规则。缺少可选条件仍保持原有的“不命中”语义，
 * 未知的宿主规则不会因本策略而失去评估资格。</p>
 */
public final class DefaultMonitoringEventPolicy implements MonitoringEventPolicy {
    private static final String AUTH_TWO = "AUTH-02";
    private static final String SESS_ONE = "SESS-01";
    private static final String AUTHZ_TWO = "AUTHZ-02";
    private static final String DATA_TWO = "DATA-02";
    private static final String DATA_THREE = "DATA-03";
    private static final String EXPORT_ONE = "EXPT-01";
    private static final String EXPORT_TWO = "EXPT-02";
    private static final String PRIVILEGE_ONE = "PRIV-01";
    private static final String PRIVILEGE_TWO = "PRIV-02";

    @Override
    public EventInputValidation validate(SecurityEventDraft draft, Set<String> enabledRuleIds) {
        Objects.requireNonNull(draft, "draft");
        Objects.requireNonNull(enabledRuleIds, "enabledRuleIds");
        Map<String, EventInputIssue> issues = new LinkedHashMap<String, EventInputIssue>();

        validateIpRule(draft, enabledRuleIds, issues);
        validateSessionRule(draft, enabledRuleIds, issues);
        validateResourceRules(draft, enabledRuleIds, issues);
        validateSensitiveDataRule(draft, enabledRuleIds, issues);
        validateExportRules(draft, enabledRuleIds, issues);
        validatePrivilegeRules(draft, enabledRuleIds, issues);

        return issues.isEmpty() ? EventInputValidation.valid()
            : EventInputValidation.incomplete(issues.values(), issues.keySet());
    }

    private static void validateIpRule(SecurityEventDraft draft, Set<String> enabledRuleIds,
                                       Map<String, EventInputIssue> issues) {
        if (isEnabled(enabledRuleIds, AUTH_TWO)
            && draft.getEventType() == SecurityEventType.LOGIN_FAILURE
            && !isIpLiteral(draft.getSourceIp())) {
            addInvalid(issues, AUTH_TWO, "sourceIp", EventInputIssueCode.INVALID_SOURCE_IP,
                EventFactSource.TRUSTED_REQUEST);
        }
    }

    private static void validateSessionRule(SecurityEventDraft draft, Set<String> enabledRuleIds,
                                            Map<String, EventInputIssue> issues) {
        if (isEnabled(enabledRuleIds, SESS_ONE)
            && draft.getEventType() == SecurityEventType.SESSION_CONCURRENT) {
            String differentNetworks = draft.getAttribute("different_networks");
            addInvalidBooleanWhenPresent(issues, SESS_ONE, "different_networks",
                differentNetworks);
            if (isStrictTrue(differentNetworks) && !draft.hasDataCount()) {
                addMissing(issues, SESS_ONE, "dataCount", EventFactSource.SERVER_COMPUTED);
            }
        }
    }

    private static void validateResourceRules(SecurityEventDraft draft, Set<String> enabledRuleIds,
                                              Map<String, EventInputIssue> issues) {
        String sequentialAccess = draft.getAttribute("sequential_access");
        if (isEnabled(enabledRuleIds, AUTHZ_TWO)) {
            if (isPresent(sequentialAccess) && !isStrictBoolean(sequentialAccess)) {
                addInvalidBooleanWhenPresent(issues, AUTHZ_TWO, "sequential_access", sequentialAccess);
            } else if (isStrictTrue(sequentialAccess) && !hasText(draft.getResourceId())) {
                addMissing(issues, AUTHZ_TWO, "resourceId", EventFactSource.METHOD_PARAMETER);
            }
        }
        if (isEnabled(enabledRuleIds, DATA_TWO)
            && (draft.getEventType() == SecurityEventType.QUERY
                || draft.getEventType() == SecurityEventType.VIEW_SENSITIVE)
            && !hasText(draft.getResourceId())) {
            addMissing(issues, DATA_TWO, "resourceId", EventFactSource.METHOD_PARAMETER);
        }
    }

    private static void validateSensitiveDataRule(SecurityEventDraft draft, Set<String> enabledRuleIds,
                                                  Map<String, EventInputIssue> issues) {
        if (!isEnabled(enabledRuleIds, DATA_THREE)) {
            return;
        }
        String sensitive = draft.getAttribute("sensitive");
        String workHours = draft.getAttribute("work_hours");
        addInvalidBooleanWhenPresent(issues, DATA_THREE, "sensitive", sensitive);
        addInvalidBooleanWhenPresent(issues, DATA_THREE, "work_hours", workHours);
        if (isStrictTrue(sensitive) && isStrictFalse(workHours) && !draft.hasDataCount()) {
            addMissing(issues, DATA_THREE, "dataCount", EventFactSource.SERVER_COMPUTED);
        }
    }

    private static void validateExportRules(SecurityEventDraft draft, Set<String> enabledRuleIds,
                                            Map<String, EventInputIssue> issues) {
        if (draft.getEventType() != SecurityEventType.EXPORT) {
            return;
        }
        if (!draft.hasDataCount()) {
            if (isEnabled(enabledRuleIds, EXPORT_ONE)) {
                addMissing(issues, EXPORT_ONE, "dataCount", EventFactSource.SERVER_COMPUTED);
            }
            if (isEnabled(enabledRuleIds, EXPORT_TWO)) {
                addMissing(issues, EXPORT_TWO, "dataCount", EventFactSource.SERVER_COMPUTED);
            }
            return;
        }
        String baselineRatio = draft.getAttribute("baseline_ratio");
        if (isEnabled(enabledRuleIds, EXPORT_TWO) && isPresent(baselineRatio)
            && !isValidBaselineRatio(baselineRatio)) {
            addInvalid(issues, EXPORT_TWO, "baseline_ratio", EventInputIssueCode.INVALID_FACT,
                EventFactSource.SERVER_COMPUTED);
        }
    }

    private static void validatePrivilegeRules(SecurityEventDraft draft, Set<String> enabledRuleIds,
                                               Map<String, EventInputIssue> issues) {
        if (draft.getEventType() != SecurityEventType.ROLE_GRANT) {
            return;
        }
        if (isEnabled(enabledRuleIds, PRIVILEGE_ONE)) {
            String targetUserId = draft.getAttribute("target_user_id");
            String privilegeIncrease = draft.getAttribute("privilege_increase");
            if (!hasText(targetUserId)) {
                addMissing(issues, PRIVILEGE_ONE, "targetUserId", EventFactSource.METHOD_PARAMETER);
            } else if (!hasText(privilegeIncrease)) {
                addMissing(issues, PRIVILEGE_ONE, "privilege_increase", EventFactSource.METHOD_PARAMETER);
            } else if (!isStrictBoolean(privilegeIncrease)) {
                addInvalid(issues, PRIVILEGE_ONE, "privilege_increase", EventInputIssueCode.INVALID_FACT,
                    EventFactSource.METHOD_PARAMETER);
            }
        }
        if (isEnabled(enabledRuleIds, PRIVILEGE_TWO)) {
            addInvalidBooleanWhenPresent(issues, PRIVILEGE_TWO, "high_privilege",
                draft.getAttribute("high_privilege"));
        }
    }

    private static boolean isEnabled(Set<String> enabledRuleIds, String ruleId) {
        return enabledRuleIds.contains(ruleId);
    }

    private static void addMissing(Map<String, EventInputIssue> issues, String ruleId, String factName,
                                   EventFactSource sourceType) {
        if (!issues.containsKey(ruleId)) {
            issues.put(ruleId, EventInputIssue.missing(ruleId, factName, sourceType));
        }
    }

    private static void addInvalid(Map<String, EventInputIssue> issues, String ruleId, String factName,
                                   EventInputIssueCode issueCode, EventFactSource sourceType) {
        if (!issues.containsKey(ruleId)) {
            issues.put(ruleId, EventInputIssue.of(ruleId, factName, issueCode, sourceType));
        }
    }

    private static void addInvalidBooleanWhenPresent(Map<String, EventInputIssue> issues, String ruleId,
                                                     String factName, String value) {
        if (isPresent(value) && !isStrictBoolean(value)) {
            addInvalid(issues, ruleId, factName, EventInputIssueCode.INVALID_FACT,
                EventFactSource.METHOD_PARAMETER);
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static boolean isPresent(String value) {
        return value != null;
    }

    private static boolean isStrictBoolean(String value) {
        return "true".equals(value) || "false".equals(value);
    }

    private static boolean isStrictTrue(String value) {
        return "true".equals(value);
    }

    private static boolean isStrictFalse(String value) {
        return "false".equals(value);
    }

    private static boolean isValidBaselineRatio(String value) {
        try {
            double parsed = Double.parseDouble(value);
            return !Double.isNaN(parsed) && !Double.isInfinite(parsed) && parsed >= 0.0d;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private static boolean isIpLiteral(String value) {
        return isIpv4Literal(value) || isIpv6Literal(value);
    }

    private static boolean isIpv4Literal(String value) {
        if (value == null) {
            return false;
        }
        String[] parts = value.split("\\.", -1);
        if (parts.length != 4) {
            return false;
        }
        for (String part : parts) {
            if (part.isEmpty() || part.length() > 3 || (part.length() > 1 && part.charAt(0) == '0')) {
                return false;
            }
            int number = 0;
            for (int index = 0; index < part.length(); index++) {
                char character = part.charAt(index);
                if (character < '0' || character > '9') {
                    return false;
                }
                number = number * 10 + character - '0';
            }
            if (number > 255) {
                return false;
            }
        }
        return true;
    }

    private static boolean isIpv6Literal(String value) {
        if (value == null || value.indexOf(':') < 0 || value.indexOf('%') >= 0
            || value.indexOf('[') >= 0 || value.indexOf(']') >= 0 || value.indexOf(":::") >= 0) {
            return false;
        }
        int compressedAt = value.indexOf("::");
        if (compressedAt >= 0 && compressedAt != value.lastIndexOf("::")) {
            return false;
        }
        boolean compressed = compressedAt >= 0;
        if ((!compressed && (value.startsWith(":") || value.endsWith(":")))
            || (compressed && value.startsWith(":") && !value.startsWith("::"))
            || (compressed && value.endsWith(":") && !value.endsWith("::"))) {
            return false;
        }
        String[] parts = value.split(":", -1);
        int groups = 0;
        int lastNonEmpty = -1;
        for (int index = 0; index < parts.length; index++) {
            if (!parts[index].isEmpty()) {
                lastNonEmpty = index;
            }
        }
        for (int index = 0; index < parts.length; index++) {
            String part = parts[index];
            if (part.isEmpty()) {
                continue;
            }
            if (part.indexOf('.') >= 0) {
                if (index != lastNonEmpty || !isIpv4Literal(part)) {
                    return false;
                }
                groups += 2;
            } else {
                if (part.length() > 4) {
                    return false;
                }
                for (int characterIndex = 0; characterIndex < part.length(); characterIndex++) {
                    if (Character.digit(part.charAt(characterIndex), 16) < 0) {
                        return false;
                    }
                }
                groups++;
            }
        }
        return compressed ? groups < 8 : groups == 8;
    }
}

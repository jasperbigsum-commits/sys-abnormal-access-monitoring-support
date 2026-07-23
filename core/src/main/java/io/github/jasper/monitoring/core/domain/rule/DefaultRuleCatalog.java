package io.github.jasper.monitoring.core.domain.rule;




import io.github.jasper.monitoring.core.domain.SecurityEvent;
import io.github.jasper.monitoring.core.domain.RuleMatch;



import io.github.jasper.monitoring.api.ControlActionType;
import io.github.jasper.monitoring.api.RiskLevel;
import io.github.jasper.monitoring.api.SecurityEventType;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 建设方案提供的初始确定性规则基线。
 *
 * <p>宿主可通过 {@link DetectionRule} 接入点扩展或替换规则；规则标识应保持稳定，
 * 以保证告警关联、白名单与历史审计可追溯。</p>
 */
public final class DefaultRuleCatalog {
    private DefaultRuleCatalog() { }

    /** @return 十四条按稳定顺序评估的确定性基线规则 */
    public static List<DetectionRule> initialRules() {
        return Arrays.<DetectionRule>asList(
            authOne(),
            authTwo(),
            new EventConditionRule("AUTH-03", event -> event.getEventType() == SecurityEventType.LOGIN_FAILURE
                && ("DISABLED".equalsIgnoreCase(event.getAttribute("account_status"))
                || "ACCOUNT_DISABLED".equalsIgnoreCase(event.getReasonCode())), RiskLevel.HIGH,
                actions(ControlActionType.DENY), "disabled account attempted login"),
            new EventConditionRule("SESS-01", event -> event.getEventType() == SecurityEventType.SESSION_CONCURRENT
                && event.getDataCount() >= 3 && truthy(event.getAttribute("different_networks")), RiskLevel.MEDIUM,
                actions(ControlActionType.REQUIRE_MFA), "concurrent sessions from different networks"),
            new WindowAggregateRule("AUTHZ-01", event -> event.getEventType() == SecurityEventType.ACCESS_DENIED
                || event.getEventType() == SecurityEventType.RESOURCE_SCOPE_DENIED,
                event -> event.getEventType() == SecurityEventType.ACCESS_DENIED
                    || event.getEventType() == SecurityEventType.RESOURCE_SCOPE_DENIED, Duration.ofMinutes(5), 10,
                WindowAggregateRule.Scope.SESSION_OR_USER, WindowAggregateRule.Aggregation.EVENT_COUNT, RiskLevel.HIGH,
                actions(ControlActionType.RATE_LIMIT), "repeated access denied"),
            new WindowAggregateRule("AUTHZ-02", event -> truthy(event.getAttribute("sequential_access")),
                event -> event.getResourceId() != null, Duration.ofMinutes(10), 100,
                WindowAggregateRule.Scope.SESSION_OR_USER, WindowAggregateRule.Aggregation.DISTINCT_RESOURCE_COUNT,
                RiskLevel.HIGH, actions(ControlActionType.DENY, ControlActionType.REVOKE_SESSION), "sequential resource enumeration"),
            new WindowAggregateRule("DATA-01", event -> event.getEventType() == SecurityEventType.QUERY,
                event -> event.getEventType() == SecurityEventType.QUERY, Duration.ofMinutes(5), 120,
                WindowAggregateRule.Scope.USER, WindowAggregateRule.Aggregation.EVENT_COUNT, RiskLevel.MEDIUM,
                actions(ControlActionType.RATE_LIMIT), "high frequency query"),
            new WindowAggregateRule("DATA-02", event -> event.getEventType() == SecurityEventType.QUERY
                || event.getEventType() == SecurityEventType.VIEW_SENSITIVE, event -> event.getResourceId() != null,
                Duration.ofMinutes(30), 1000, WindowAggregateRule.Scope.USER,
                WindowAggregateRule.Aggregation.DISTINCT_RESOURCE_COUNT, RiskLevel.HIGH,
                actions(ControlActionType.DENY, ControlActionType.REQUIRE_APPROVAL), "large range access"),
            new WindowAggregateRule("DATA-03", event -> truthy(event.getAttribute("sensitive"))
                && "false".equalsIgnoreCase(event.getAttribute("work_hours")), event -> truthy(event.getAttribute("sensitive"))
                && "false".equalsIgnoreCase(event.getAttribute("work_hours")), Duration.ofHours(24), 50,
                WindowAggregateRule.Scope.USER, WindowAggregateRule.Aggregation.DATA_COUNT, RiskLevel.MEDIUM,
                actions(ControlActionType.REQUIRE_MFA), "sensitive access outside work hours"),
            new EventConditionRule("EXPT-01", event -> event.getEventType() == SecurityEventType.EXPORT
                && (event.getDataCount() >= 5000 || "HIGH".equalsIgnoreCase(event.getAttribute("sensitivity"))), RiskLevel.HIGH,
                actions(ControlActionType.DENY, ControlActionType.REQUIRE_APPROVAL), "large or highly sensitive export"),
            exportTwo(),
            new EventConditionRule("PRIV-01", event -> event.getEventType() == SecurityEventType.ROLE_GRANT
                && event.getUserId() != null && event.getUserId().equals(event.getAttribute("target_user_id"))
                && truthy(event.getAttribute("privilege_increase")), RiskLevel.HIGH,
                actions(ControlActionType.DENY), "self privilege escalation"),
            new EventConditionRule("PRIV-02", event -> event.getEventType() == SecurityEventType.ADMIN_CREATE
                || (event.getEventType() == SecurityEventType.ROLE_GRANT && truthy(event.getAttribute("high_privilege"))),
                RiskLevel.HIGH, actions(ControlActionType.REQUIRE_MFA, ControlActionType.REQUIRE_APPROVAL),
                "administrator privilege change"),
            new EventConditionRule("SECU-01", event -> event.getEventType() == SecurityEventType.RULE_CHANGE
                || event.getEventType() == SecurityEventType.AUDIT_CONFIG_CHANGE
                || event.getEventType() == SecurityEventType.SECURITY_SWITCH_CHANGE, RiskLevel.HIGH,
                actions(ControlActionType.RECORD), "security configuration changed"));
    }

    private static DetectionRule authOne() {
        return new AbstractDetectionRule("AUTH-01", RiskLevel.MEDIUM,
            actions(ControlActionType.REQUIRE_CAPTCHA, ControlActionType.RATE_LIMIT), "five or more login failures") {
            @Override public Optional<RuleMatch> evaluate(SecurityEvent event, List<SecurityEvent> history) {
                if (event.getEventType() != SecurityEventType.LOGIN_FAILURE) { return Optional.empty(); }
                int failures = 0;
                String subject = loginSubject(event);
                Instant start = event.getOccurredAt().minus(Duration.ofMinutes(5));
                for (SecurityEvent candidate : history) {
                    if (!candidate.getOccurredAt().isBefore(start) && !candidate.getOccurredAt().isAfter(event.getOccurredAt())
                        && candidate.getEventType() == SecurityEventType.LOGIN_FAILURE
                        && subject.equals(loginSubject(candidate))) {
                        failures++;
                    }
                }
                return failures >= 5 ? match(event, subject, Duration.ofMinutes(15)) : Optional.<RuleMatch>empty();
            }
        };
    }

    private static DetectionRule authTwo() {
        return new AbstractDetectionRule("AUTH-02", RiskLevel.HIGH, actions(ControlActionType.RATE_LIMIT),
            "one IP failed across accounts") {
            @Override public Optional<RuleMatch> evaluate(SecurityEvent event, List<SecurityEvent> history) {
                if (event.getEventType() != SecurityEventType.LOGIN_FAILURE) { return Optional.empty(); }
                int failures = 0; int attempts = 0; Set<String> subjects = new HashSet<String>();
                Instant start = event.getOccurredAt().minus(Duration.ofMinutes(10));
                for (SecurityEvent candidate : history) {
                    if (candidate.getOccurredAt().isBefore(start) || candidate.getOccurredAt().isAfter(event.getOccurredAt())) { continue; }
                    if (!event.getSourceIp().equals(candidate.getSourceIp())) { continue; }
                    if (candidate.getEventType() == SecurityEventType.LOGIN_FAILURE) {
                        failures++;
                        subjects.add(loginSubject(candidate));
                        attempts++;
                    }
                    if (candidate.getEventType() == SecurityEventType.LOGIN_SUCCESS) { attempts++; }
                }
                return subjects.size() >= 10 && attempts > 0 && failures * 100 >= attempts * 80
                    ? match(event, "ip:" + event.getSourceIp(), Duration.ofMinutes(30))
                    : Optional.<RuleMatch>empty();
            }
        };
    }

    private static String loginSubject(SecurityEvent event) {
        String attemptedAccountHash = event.getAttribute("attempted_account_hash");
        return attemptedAccountHash != null && !attemptedAccountHash.isEmpty()
            ? "attempted:" + attemptedAccountHash : event.subject();
    }

    private static DetectionRule exportTwo() {
        return new AbstractDetectionRule("EXPT-02", RiskLevel.HIGH, actions(ControlActionType.DENY),
            "daily export exceeds baseline") {
            @Override public Optional<RuleMatch> evaluate(SecurityEvent event, List<SecurityEvent> history) {
                if (event.getEventType() != SecurityEventType.EXPORT) { return Optional.empty(); }
                long total = 0;
                Instant start = event.getOccurredAt().atZone(ZoneOffset.UTC).truncatedTo(ChronoUnit.DAYS).toInstant();
                for (SecurityEvent candidate : history) {
                    if (!candidate.getOccurredAt().isBefore(start) && sameScope(event, candidate, WindowAggregateRule.Scope.USER)
                        && candidate.getEventType() == SecurityEventType.EXPORT) { total += candidate.getDataCount(); }
                }
                return total >= 10000 || atLeast(event.getAttribute("baseline_ratio"), 3.0d)
                    ? match(event)
                    : Optional.<RuleMatch>empty();
            }
        };
    }
    private static boolean sameScope(SecurityEvent left, SecurityEvent right, WindowAggregateRule.Scope scope) {
        if (scope == WindowAggregateRule.Scope.USER) { return left.subject().equals(right.subject()); }
        String leftSession = left.getSessionIdHash();
        return leftSession != null ? leftSession.equals(right.getSessionIdHash()) : left.subject().equals(right.subject());
    }
    private static List<ControlActionType> actions(ControlActionType... values) { return Arrays.asList(values); }
    private static boolean truthy(String value) { return "true".equalsIgnoreCase(value) || "1".equals(value); }
    private static boolean atLeast(String value, double threshold) { try { return value != null && Double.parseDouble(value) >= threshold; } catch (NumberFormatException ignored) { return false; } }
}

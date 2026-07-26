package io.github.jasper.monitoring.core.domain.rule;




import io.github.jasper.monitoring.core.domain.SecurityEvent;
import io.github.jasper.monitoring.core.domain.RuleMatch;



import io.github.jasper.monitoring.api.ControlActionType;
import io.github.jasper.monitoring.api.RiskLevel;
import io.github.jasper.monitoring.api.SecurityEventType;
import io.github.jasper.monitoring.api.action.BuiltInActions;
import io.github.jasper.monitoring.api.action.ActionType;
import io.github.jasper.monitoring.api.rule.RuleCatalog;
import io.github.jasper.monitoring.api.rule.RuleDefinition;
import io.github.jasper.monitoring.api.rule.RuleMode;
import io.github.jasper.monitoring.api.rule.RuleSource;
import io.github.jasper.monitoring.api.rule.RuleType;
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
                && falsy(event.getAttribute("work_hours")), event -> truthy(event.getAttribute("sensitive"))
                && falsy(event.getAttribute("work_hours")), Duration.ofHours(24), 50,
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

    /**
     * Returns the immutable typed registration of the fourteen built-in rules.
     * The legacy predicates remain implementation details behind the adapters below.
     */
    public static RuleCatalog typedCatalog() {
        RuleCatalog catalog = new RuleCatalog();
        for (RuleRegistration registration : registrations()) {
            register(catalog, registration);
        }
        catalog.freeze();
        return catalog;
    }

    @SuppressWarnings("unchecked")
    private static <R extends RuleType> void register(RuleCatalog catalog, RuleRegistration registration) {
        catalog.register((RuleDefinition<R>) registration.definition);
    }

    /** @return typed rule adapters in the same stable order as {@link #initialRules()} */
    public static List<DetectionRule<? extends RuleType>> typedRules() {
        List<DetectionRule<? extends RuleType>> result =
            new java.util.ArrayList<DetectionRule<? extends RuleType>>();
        for (RuleRegistration registration : registrations()) {
            result.add(registration.adapter);
        }
        return java.util.Collections.unmodifiableList(result);
    }

    private static List<RuleRegistration> registrations() {
        List<DetectionRule> legacy = initialRules();
        List<RuleRegistration> result = new java.util.ArrayList<RuleRegistration>(legacy.size());
        result.add(registration(Auth01.class, legacy.get(0), "AUTH-01", RiskLevel.MEDIUM, BuiltInActions.LoginFailure.class));
        result.add(registration(Auth02.class, legacy.get(1), "AUTH-02", RiskLevel.HIGH, BuiltInActions.LoginFailure.class));
        result.add(registration(Auth03.class, legacy.get(2), "AUTH-03", RiskLevel.HIGH, BuiltInActions.LoginFailure.class));
        result.add(registration(Sess01.class, legacy.get(3), "SESS-01", RiskLevel.MEDIUM, BuiltInActions.SessionConcurrent.class));
        result.add(registration(Authz01.class, legacy.get(4), "AUTHZ-01", RiskLevel.HIGH, BuiltInActions.AccessDenied.class));
        result.add(registration(Authz02.class, legacy.get(5), "AUTHZ-02", RiskLevel.HIGH, BuiltInActions.Query.class));
        result.add(registration(Data01.class, legacy.get(6), "DATA-01", RiskLevel.MEDIUM, BuiltInActions.Query.class));
        result.add(registration(Data02.class, legacy.get(7), "DATA-02", RiskLevel.HIGH, BuiltInActions.Query.class));
        result.add(registration(Data03.class, legacy.get(8), "DATA-03", RiskLevel.MEDIUM, BuiltInActions.SensitiveView.class));
        result.add(registration(Expt01.class, legacy.get(9), "EXPT-01", RiskLevel.HIGH, BuiltInActions.ReportExport.class));
        result.add(registration(Expt02.class, legacy.get(10), "EXPT-02", RiskLevel.HIGH, BuiltInActions.ReportExport.class));
        result.add(registration(Priv01.class, legacy.get(11), "PRIV-01", RiskLevel.HIGH, BuiltInActions.PrivilegeChange.class));
        result.add(registration(Priv02.class, legacy.get(12), "PRIV-02", RiskLevel.HIGH, BuiltInActions.PrivilegeChange.class));
        result.add(registration(Secu01.class, legacy.get(13), "SECU-01", RiskLevel.HIGH, BuiltInActions.SecurityChange.class));
        return result;
    }

    private static <R extends RuleType, A extends ActionType> RuleRegistration registration(Class<R> type, DetectionRule legacy,
                                                                        String id, RiskLevel risk, Class<A> actionType) {
        RuleDefinition<R> definition = RuleDefinition.builder(type, id)
            .appliesTo(actionType)
            .historyWindow(Duration.ofDays(1)).threshold(1L).risk(risk)
            .mode(RuleMode.OBSERVE).source(RuleSource.INTERNAL)
            .control(ControlActionType.RECORD).build();
        return new RuleRegistration(type, definition, new LegacyRuleAdapter<R>(legacy, definition));
    }

    private static final class RuleRegistration {
        private final Class<? extends RuleType> type;
        private final RuleDefinition<?> definition;
        private final DetectionRule<? extends RuleType> adapter;
        private <R extends RuleType> RuleRegistration(Class<R> type, RuleDefinition<R> definition,
                                                       DetectionRule<R> adapter) {
            this.type = type;
            this.definition = definition;
            this.adapter = adapter;
        }
    }

    /** Explicit compatibility adapter; legacy predicates never become typed rules implicitly. */
    private static final class LegacyRuleAdapter<R extends RuleType> implements DetectionRule<R> {
        private final DetectionRule legacy;
        private final RuleDefinition<R> definition;
        private LegacyRuleAdapter(DetectionRule legacy, RuleDefinition<R> definition) {
            this.legacy = legacy;
            this.definition = definition;
        }
        @Override public RuleDefinition<R> definition() { return definition; }
        @Override public Optional<RuleMatch> evaluate(RuleEvaluationContext context) {
            return legacy.evaluate(context.getEvent(), context.getHistory());
        }
    }

    static final class Auth01 implements RuleType { private Auth01() { } }
    static final class Auth02 implements RuleType { private Auth02() { } }
    static final class Auth03 implements RuleType { private Auth03() { } }
    static final class Sess01 implements RuleType { private Sess01() { } }
    static final class Authz01 implements RuleType { private Authz01() { } }
    static final class Authz02 implements RuleType { private Authz02() { } }
    static final class Data01 implements RuleType { private Data01() { } }
    static final class Data02 implements RuleType { private Data02() { } }
    static final class Data03 implements RuleType { private Data03() { } }
    static final class Expt01 implements RuleType { private Expt01() { } }
    static final class Expt02 implements RuleType { private Expt02() { } }
    static final class Priv01 implements RuleType { private Priv01() { } }
    static final class Priv02 implements RuleType { private Priv02() { } }
    static final class Secu01 implements RuleType { private Secu01() { } }

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
                    if (!candidate.getOccurredAt().isBefore(start)
                        && !candidate.getOccurredAt().isAfter(event.getOccurredAt())
                        && sameScope(event, candidate, WindowAggregateRule.Scope.USER)
                        && candidate.getEventType() == SecurityEventType.EXPORT
                        && candidate.hasDataCount()) { total = addSaturated(total, candidate.getDataCount()); }
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
    private static boolean truthy(String value) { return "true".equals(value); }
    private static boolean falsy(String value) { return "false".equals(value); }
    private static boolean atLeast(String value, double threshold) {
        if (value == null) {
            return false;
        }
        try {
            double parsed = Double.parseDouble(value);
            return !Double.isNaN(parsed) && !Double.isInfinite(parsed) && parsed >= threshold;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }
    private static long addSaturated(long total, long value) {
        return Long.MAX_VALUE - total < value ? Long.MAX_VALUE : total + value;
    }
}

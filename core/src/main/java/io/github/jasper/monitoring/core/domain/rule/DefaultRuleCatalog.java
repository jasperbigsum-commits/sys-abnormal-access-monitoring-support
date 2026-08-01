package io.github.jasper.monitoring.core.domain.rule;

import io.github.jasper.monitoring.api.ControlActionType;
import io.github.jasper.monitoring.api.RiskLevel;
import io.github.jasper.monitoring.api.SecurityEventType;
import io.github.jasper.monitoring.api.SecurityEventResult;
import io.github.jasper.monitoring.api.action.ActionType;
import io.github.jasper.monitoring.api.action.ActionDisposition;
import io.github.jasper.monitoring.api.action.ActionRequirement;
import io.github.jasper.monitoring.api.action.BuiltInActions;
import io.github.jasper.monitoring.api.code.BuiltInReasonCodes;
import io.github.jasper.monitoring.api.fact.BuiltInFacts;
import io.github.jasper.monitoring.api.control.ControlType;
import io.github.jasper.monitoring.api.rule.RuleCatalog;
import io.github.jasper.monitoring.api.rule.RuleDefinition;
import io.github.jasper.monitoring.api.rule.RuleMode;
import io.github.jasper.monitoring.api.rule.RuleSource;
import io.github.jasper.monitoring.api.rule.RuleType;
import io.github.jasper.monitoring.core.domain.RuleMatch;
import io.github.jasper.monitoring.core.domain.SecurityEvent;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Fourteen deterministic built-in rules with one typed definition and execution path. */
public final class DefaultRuleCatalog {
    private DefaultRuleCatalog() {
    }

    /** Returns the immutable rules in their stable evaluation order. */
    public static List<DetectionRule<? extends RuleType>> typedRules() {
        List<DetectionRule<? extends RuleType>> rules = new ArrayList<DetectionRule<? extends RuleType>>();
        rules.add(authOne());
        rules.add(authTwo());
        rules.add(condition(definition(Auth03.class, "AUTH-03", RiskLevel.HIGH,
            BuiltInActions.Login.class, Duration.ZERO, 1L, ControlActionType.DENY),
            event -> event.getEventType() == SecurityEventType.LOGIN_FAILURE
                && BuiltInReasonCodes.Authentication.ACCOUNT_DISABLED.getCode().equals(event.getReasonCode()),
            "disabled account attempted login"));
        rules.add(condition(definition(Sess01.class, "SESS-01", RiskLevel.MEDIUM,
            BuiltInActions.SessionConcurrent.class, Duration.ZERO, 1L, ControlActionType.REQUIRE_MFA),
            event -> event.getEventType() == SecurityEventType.SESSION_CONCURRENT
                && event.getDataCount() >= 3 && truthy(event.getAttribute("different_networks")),
            "concurrent sessions from different networks"));
        rules.add(window(definition(Authz01.class, "AUTHZ-01", RiskLevel.HIGH,
                BuiltInActions.AccessDenied.class, Duration.ofMinutes(5), 10L, ControlActionType.RATE_LIMIT),
            event -> event.getEventType() == SecurityEventType.ACCESS_DENIED
                || event.getEventType() == SecurityEventType.RESOURCE_SCOPE_DENIED,
            event -> event.getEventType() == SecurityEventType.ACCESS_DENIED
                || event.getEventType() == SecurityEventType.RESOURCE_SCOPE_DENIED,
            WindowAggregateRule.Scope.SESSION_OR_USER, WindowAggregateRule.Aggregation.EVENT_COUNT,
            "repeated access denied"));
        rules.add(window(definition(Authz02.class, "AUTHZ-02", RiskLevel.HIGH,
                BuiltInActions.Query.class, Duration.ofMinutes(10), 100L,
                ControlActionType.DENY, ControlActionType.REVOKE_SESSION),
            event -> truthy(event.getAttribute("sequential_access")),
            event -> event.getResourceId() != null,
            WindowAggregateRule.Scope.SESSION_OR_USER, WindowAggregateRule.Aggregation.DISTINCT_RESOURCE_COUNT,
            "sequential resource enumeration"));
        rules.add(window(definition(Data01.class, "DATA-01", RiskLevel.MEDIUM,
                BuiltInActions.Query.class, Duration.ofMinutes(5), 120L, ControlActionType.RATE_LIMIT),
            event -> event.getEventType() == SecurityEventType.QUERY,
            event -> event.getEventType() == SecurityEventType.QUERY,
            WindowAggregateRule.Scope.USER, WindowAggregateRule.Aggregation.EVENT_COUNT,
            "high frequency query"));
        rules.add(window(definition(Data02.class, "DATA-02", RiskLevel.HIGH,
                BuiltInActions.Query.class, Duration.ofMinutes(30), 1000L,
                ControlActionType.DENY, ControlActionType.REQUIRE_APPROVAL),
            event -> event.getEventType() == SecurityEventType.QUERY
                || event.getEventType() == SecurityEventType.VIEW_SENSITIVE,
            event -> event.getResourceId() != null,
            WindowAggregateRule.Scope.USER, WindowAggregateRule.Aggregation.DISTINCT_RESOURCE_COUNT,
            "large range access"));
        rules.add(window(definition(Data03.class, "DATA-03", RiskLevel.MEDIUM,
                BuiltInActions.SensitiveView.class, Duration.ofHours(24), 50L, ControlActionType.REQUIRE_MFA),
            event -> truthy(event.getAttribute("sensitive")) && falsy(event.getAttribute("work_hours")),
            event -> truthy(event.getAttribute("sensitive")) && falsy(event.getAttribute("work_hours")),
            WindowAggregateRule.Scope.USER, WindowAggregateRule.Aggregation.DATA_COUNT,
            "sensitive access outside work hours"));
        rules.add(condition(blockingDefinition(Expt01.class, "EXPT-01", RiskLevel.HIGH,
            BuiltInActions.ReportExport.class, Duration.ZERO, 1L, ActionRequirement.APPROVAL),
            event -> event.getEventType() == SecurityEventType.EXPORT
                && (event.getDataCount() >= 5000
                    || "HIGH".equalsIgnoreCase(event.getAttribute("sensitivity"))),
            "large or highly sensitive export"));
        rules.add(exportTwo());
        rules.add(condition(definition(Priv01.class, "PRIV-01", RiskLevel.HIGH,
            BuiltInActions.PrivilegeChange.class, Duration.ZERO, 1L, ControlActionType.DENY),
            event -> event.getEventType() == SecurityEventType.ROLE_GRANT
                && event.getUserId() != null
                && event.getUserId().equals(event.getAttribute("target_user_id"))
                && truthy(event.getAttribute("privilege_increase")),
            "self privilege escalation"));
        rules.add(condition(definition(Priv02.class, "PRIV-02", RiskLevel.HIGH,
            BuiltInActions.PrivilegeChange.class, Duration.ZERO, 1L,
            ControlActionType.REQUIRE_MFA, ControlActionType.REQUIRE_APPROVAL),
            event -> event.getEventType() == SecurityEventType.ADMIN_CREATE
                || (event.getEventType() == SecurityEventType.ROLE_GRANT
                    && truthy(event.getAttribute("high_privilege"))),
            "administrator privilege change"));
        rules.add(condition(definition(Secu01.class, "SECU-01", RiskLevel.HIGH,
            BuiltInActions.SecurityChange.class, Duration.ZERO, 1L, ControlActionType.RECORD),
            event -> event.getEventType() == SecurityEventType.RULE_CHANGE
                || event.getEventType() == SecurityEventType.AUDIT_CONFIG_CHANGE
                || event.getEventType() == SecurityEventType.SECURITY_SWITCH_CHANGE,
            "security configuration changed"));
        return Collections.unmodifiableList(rules);
    }

    /** Returns the frozen definitions for the executable built-in rules. */
    public static RuleCatalog typedCatalog() {
        RuleCatalog catalog = new RuleCatalog();
        for (DetectionRule<? extends RuleType> rule : typedRules()) {
            register(catalog, rule.definition());
        }
        catalog.freeze();
        return catalog;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void register(RuleCatalog catalog, RuleDefinition<?> definition) {
        catalog.register((RuleDefinition) definition);
    }

    /** Returns every executable control that an enabled built-in rule may emit. */
    public static Set<ControlType> requiredControlTypes() {
        return typedCatalog().requiredControlTypes();
    }

    private static <R extends RuleType, A extends ActionType> RuleDefinition<R> definition(
            Class<R> type, String id, RiskLevel risk, Class<A> actionType,
            Duration historyWindow, long threshold, ControlActionType... controls) {
        RuleDefinition.Builder<R> builder = RuleDefinition.builder(type, id)
            .appliesTo(actionType)
            .historyWindow(historyWindow)
            .threshold(threshold)
            .risk(risk)
            .mode(RuleMode.ENFORCE)
            .source(RuleSource.INTERNAL);
        for (ControlActionType control : controls) {
            builder.control(control);
        }
        return builder.build();
    }

    private static <R extends RuleType, A extends ActionType> RuleDefinition<R> blockingDefinition(
            Class<R> type, String id, RiskLevel risk, Class<A> actionType,
            Duration historyWindow, long threshold, ActionRequirement... requirements) {
        RuleDefinition.Builder<R> builder = RuleDefinition.builder(type, id)
            .appliesTo(actionType)
            .historyWindow(historyWindow)
            .threshold(threshold)
            .risk(risk)
            .disposition(ActionDisposition.BLOCK)
            .mode(RuleMode.ENFORCE)
            .source(RuleSource.INTERNAL);
        for (ActionRequirement requirement : requirements) builder.requirement(requirement);
        return builder.build();
    }

    private static <R extends RuleType> DetectionRule<R> condition(RuleDefinition<R> definition,
            java.util.function.Predicate<SecurityEvent> condition, String reason) {
        return new EventConditionRule<R>(definition, condition, reason);
    }

    private static <R extends RuleType> DetectionRule<R> window(RuleDefinition<R> definition,
            java.util.function.Predicate<SecurityEvent> trigger,
            java.util.function.Predicate<SecurityEvent> candidate,
            WindowAggregateRule.Scope scope, WindowAggregateRule.Aggregation aggregation, String reason) {
        return new WindowAggregateRule<R>(definition, trigger, candidate, scope, aggregation, reason);
    }

    private static DetectionRule<Auth01> authOne() {
        final RuleDefinition<Auth01> definition = definition(Auth01.class, "AUTH-01", RiskLevel.MEDIUM,
            BuiltInActions.Login.class, Duration.ofMinutes(5), 5L,
            ControlActionType.REQUIRE_CAPTCHA, ControlActionType.RATE_LIMIT);
        return new AbstractDetectionRule<Auth01>(definition, "five or more login failures") {
            @Override
            public Optional<RuleMatch> evaluate(RuleEvaluationContext context) {
                SecurityEvent event = context.getEvent();
                if (event.getEventType() != SecurityEventType.LOGIN_FAILURE) {
                    return Optional.empty();
                }
                int failures = 0;
                String subject = loginSubject(event);
                if (subject == null) return Optional.empty();
                Instant start = event.getOccurredAt().minus(definition.getHistoryWindow());
                for (SecurityEvent candidate : context.getHistory()) {
                    if (!candidate.getOccurredAt().isBefore(start)
                            && !candidate.getOccurredAt().isAfter(event.getOccurredAt())
                            && candidate.getEventType() == SecurityEventType.LOGIN_FAILURE
                            && subject.equals(loginSubject(candidate))) {
                        failures++;
                    }
                }
                return failures >= definition.getThreshold()
                    ? match(event, subject, Duration.ofMinutes(15)) : Optional.<RuleMatch>empty();
            }
        };
    }

    private static DetectionRule<Auth02> authTwo() {
        final RuleDefinition<Auth02> definition = definition(Auth02.class, "AUTH-02", RiskLevel.HIGH,
            BuiltInActions.Login.class, Duration.ofMinutes(10), 10L, ControlActionType.RATE_LIMIT);
        return new AbstractDetectionRule<Auth02>(definition, "one IP failed across accounts") {
            @Override
            public Optional<RuleMatch> evaluate(RuleEvaluationContext context) {
                SecurityEvent event = context.getEvent();
                if (event.getEventType() != SecurityEventType.LOGIN_FAILURE) {
                    return Optional.empty();
                }
                int failures = 0;
                int attempts = 0;
                Set<String> subjects = new HashSet<String>();
                Instant start = event.getOccurredAt().minus(definition.getHistoryWindow());
                for (SecurityEvent candidate : context.getHistory()) {
                    if (candidate.getOccurredAt().isBefore(start)
                            || candidate.getOccurredAt().isAfter(event.getOccurredAt())
                            || !event.getSourceIp().equals(candidate.getSourceIp())) {
                        continue;
                    }
                    if (candidate.getEventType() == SecurityEventType.LOGIN_FAILURE) {
                        failures++;
                        String subject = loginSubject(candidate);
                        if (subject != null) subjects.add(subject);
                        attempts++;
                    }
                    if (candidate.getEventType() == SecurityEventType.LOGIN_SUCCESS) {
                        attempts++;
                    }
                }
                return subjects.size() >= definition.getThreshold()
                    && attempts > 0 && failures * 100 >= attempts * 80
                    ? match(event, "ip:" + event.getSourceIp(), Duration.ofMinutes(30))
                    : Optional.<RuleMatch>empty();
            }
        };
    }

    private static DetectionRule<Expt02> exportTwo() {
        final RuleDefinition<Expt02> definition = blockingDefinition(Expt02.class, "EXPT-02", RiskLevel.HIGH,
            BuiltInActions.ReportExport.class, Duration.ofDays(1), 10000L);
        return new AbstractDetectionRule<Expt02>(definition, "daily export exceeds baseline") {
            @Override
            public Optional<RuleMatch> evaluate(RuleEvaluationContext context) {
                SecurityEvent event = context.getEvent();
                if (event.getEventType() != SecurityEventType.EXPORT) {
                    return Optional.empty();
                }
                long total = 0L;
                Instant start = event.getOccurredAt().atZone(ZoneOffset.UTC)
                    .truncatedTo(ChronoUnit.DAYS).toInstant();
                for (SecurityEvent candidate : context.getHistory()) {
                    if (!candidate.getOccurredAt().isBefore(start)
                            && !candidate.getOccurredAt().isAfter(event.getOccurredAt())
                            && candidate != event
                            && (candidate.getEventId() == null || event.getEventId() == null
                                || !candidate.getEventId().equals(event.getEventId()))
                            && event.subject().equals(candidate.subject())
                            && candidate.getEventType() == SecurityEventType.EXPORT
                            && candidate.getResult() == SecurityEventResult.SUCCESS
                            && candidate.hasDataCount()) {
                        total = addSaturated(total, candidate.getDataCount());
                    }
                }
                if (event.hasDataCount()) {
                    total = addSaturated(total, event.getDataCount());
                }
                return total >= definition.getThreshold()
                    || atLeast(event.getAttribute("baseline_ratio"), 3.0d)
                    ? match(event) : Optional.<RuleMatch>empty();
            }
        };
    }

    private static String loginSubject(SecurityEvent event) {
        return event.getFact(BuiltInFacts.LOGIN_SUBJECT_KEY).orElse(null);
    }

    private static boolean truthy(String value) {
        return "true".equals(value);
    }

    private static boolean falsy(String value) {
        return "false".equals(value);
    }

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
}

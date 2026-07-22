package io.github.jasper.monitoring.core;

import io.github.jasper.monitoring.api.ControlActionType;
import io.github.jasper.monitoring.api.RiskLevel;
import io.github.jasper.monitoring.api.SecurityEventResult;
import io.github.jasper.monitoring.api.SecurityEventType;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Initial deterministic rule baseline from the construction plan.
 * Hosts may replace or extend this catalog through the {@link DetectionRule} integration point.
 */
public final class DefaultRuleCatalog {
    private DefaultRuleCatalog() { }

    /**
     * @return the fourteen deterministic baseline rules, in stable evaluation order
     */
    public static List<DetectionRule> initialRules() {
        return Arrays.<DetectionRule>asList(
            threshold("AUTH-01", SecurityEventType.LOGIN_FAILURE, Duration.ofMinutes(5), 6, RiskLevel.MEDIUM,
                actions(ControlActionType.REQUIRE_CAPTCHA, ControlActionType.RATE_LIMIT),
                "more than five login failures", Scope.USER),
            authTwo(),
            authThree(),
            sessionOne(),
            threshold("AUTHZ-01", SecurityEventType.ACCESS_DENIED, Duration.ofMinutes(5), 10, RiskLevel.HIGH,
                actions(ControlActionType.RATE_LIMIT), "repeated access denied", Scope.SESSION_OR_USER),
            authzTwo(),
            threshold("DATA-01", SecurityEventType.QUERY, Duration.ofMinutes(5), 120, RiskLevel.MEDIUM,
                actions(ControlActionType.RATE_LIMIT), "high frequency query", Scope.USER),
            dataTwo(),
            dataThree(),
            exportOne(),
            exportTwo(),
            privilegeOne(),
            privilegeTwo(),
            securityOne());
    }

    private static DetectionRule threshold(final String id, final SecurityEventType type, final Duration window,
                                           final int threshold, final RiskLevel level, final List<ControlActionType> actions,
                                           final String reason, final Scope scope) {
        return new BaseRule(id) {
            @Override
            public Optional<RuleMatch> evaluate(SecurityEvent event, List<SecurityEvent> history) {
                if (event.getEventType() != type) { return Optional.empty(); }
                int count = 0;
                for (SecurityEvent candidate : recent(event, history, window)) {
                    if (candidate.getEventType() == type && sameScope(event, candidate, scope)) { count++; }
                }
                return count >= threshold ? match(event, level, actions, reason) : Optional.<RuleMatch>empty();
            }
        };
    }

    private static DetectionRule authTwo() {
        return new BaseRule("AUTH-02") {
            @Override public Optional<RuleMatch> evaluate(SecurityEvent event, List<SecurityEvent> history) {
                if (event.getEventType() != SecurityEventType.LOGIN_FAILURE) { return Optional.empty(); }
                int failures = 0; int attempts = 0; Set<String> users = new HashSet<String>();
                for (SecurityEvent candidate : recent(event, history, Duration.ofMinutes(10))) {
                    if (!event.getSourceIp().equals(candidate.getSourceIp())) { continue; }
                    if (candidate.getEventType() == SecurityEventType.LOGIN_FAILURE) { failures++; users.add(candidate.subject()); attempts++; }
                    if (candidate.getEventType() == SecurityEventType.LOGIN_SUCCESS) { attempts++; }
                }
                return users.size() >= 10 && attempts > 0 && failures * 100 >= attempts * 80
                    ? match(event, RiskLevel.HIGH, actions(ControlActionType.RATE_LIMIT),
                        "one IP failed across accounts", "ip:" + event.getSourceIp(), Duration.ofMinutes(30))
                    : Optional.<RuleMatch>empty();
            }
        };
    }

    private static DetectionRule authThree() {
        return new BaseRule("AUTH-03") {
            @Override public Optional<RuleMatch> evaluate(SecurityEvent event, List<SecurityEvent> history) {
                boolean disabled = "DISABLED".equalsIgnoreCase(event.getAttribute("account_status"))
                    || "ACCOUNT_DISABLED".equalsIgnoreCase(event.getReasonCode());
                return event.getEventType() == SecurityEventType.LOGIN_FAILURE && disabled
                    ? match(event, RiskLevel.HIGH, actions(ControlActionType.DENY), "disabled account attempted login") : Optional.<RuleMatch>empty();
            }
        };
    }

    private static DetectionRule sessionOne() {
        return new BaseRule("SESS-01") {
            @Override public Optional<RuleMatch> evaluate(SecurityEvent event, List<SecurityEvent> history) {
                return event.getEventType() == SecurityEventType.SESSION_CONCURRENT && event.getDataCount() >= 3
                    && truthy(event.getAttribute("different_networks"))
                    ? match(event, RiskLevel.MEDIUM, actions(ControlActionType.REQUIRE_MFA), "concurrent sessions from different networks")
                    : Optional.<RuleMatch>empty();
            }
        };
    }

    private static DetectionRule authzTwo() {
        return new BaseRule("AUTHZ-02") {
            @Override public Optional<RuleMatch> evaluate(SecurityEvent event, List<SecurityEvent> history) {
                if (!truthy(event.getAttribute("sequential_access"))) { return Optional.empty(); }
                Set<String> resources = new HashSet<String>();
                for (SecurityEvent candidate : recent(event, history, Duration.ofMinutes(10))) {
                    if (sameScope(event, candidate, Scope.SESSION_OR_USER) && candidate.getResourceId() != null) { resources.add(candidate.getResourceId()); }
                }
                return resources.size() >= 100
                    ? match(event, RiskLevel.HIGH, actions(ControlActionType.DENY, ControlActionType.REVOKE_SESSION), "sequential resource enumeration")
                    : Optional.<RuleMatch>empty();
            }
        };
    }

    private static DetectionRule dataTwo() {
        return new BaseRule("DATA-02") {
            @Override public Optional<RuleMatch> evaluate(SecurityEvent event, List<SecurityEvent> history) {
                if (event.getEventType() != SecurityEventType.QUERY && event.getEventType() != SecurityEventType.VIEW_SENSITIVE) { return Optional.empty(); }
                Set<String> resources = new HashSet<String>();
                for (SecurityEvent candidate : recent(event, history, Duration.ofMinutes(30))) {
                    if (sameScope(event, candidate, Scope.USER) && candidate.getResourceId() != null) { resources.add(candidate.getResourceId()); }
                }
                return resources.size() >= 1000
                    ? match(event, RiskLevel.HIGH, actions(ControlActionType.DENY, ControlActionType.REQUIRE_APPROVAL), "large range access")
                    : Optional.<RuleMatch>empty();
            }
        };
    }

    private static DetectionRule dataThree() {
        return new BaseRule("DATA-03") {
            @Override public Optional<RuleMatch> evaluate(SecurityEvent event, List<SecurityEvent> history) {
                if (!truthy(event.getAttribute("sensitive")) || !"false".equalsIgnoreCase(event.getAttribute("work_hours"))) { return Optional.empty(); }
                long count = 0;
                for (SecurityEvent candidate : recent(event, history, Duration.ofHours(24))) {
                    if (sameScope(event, candidate, Scope.USER) && truthy(candidate.getAttribute("sensitive"))
                        && "false".equalsIgnoreCase(candidate.getAttribute("work_hours"))) { count += Math.max(1, candidate.getDataCount()); }
                }
                return count >= 50 ? match(event, RiskLevel.MEDIUM, actions(ControlActionType.REQUIRE_MFA), "sensitive access outside work hours")
                    : Optional.<RuleMatch>empty();
            }
        };
    }

    private static DetectionRule exportOne() {
        return new BaseRule("EXPT-01") {
            @Override public Optional<RuleMatch> evaluate(SecurityEvent event, List<SecurityEvent> history) {
                boolean highSensitivity = "HIGH".equalsIgnoreCase(event.getAttribute("sensitivity"));
                return event.getEventType() == SecurityEventType.EXPORT && (event.getDataCount() >= 5000 || highSensitivity)
                    ? match(event, RiskLevel.HIGH, actions(ControlActionType.DENY, ControlActionType.REQUIRE_APPROVAL), "large or highly sensitive export")
                    : Optional.<RuleMatch>empty();
            }
        };
    }

    private static DetectionRule exportTwo() {
        return new BaseRule("EXPT-02") {
            @Override public Optional<RuleMatch> evaluate(SecurityEvent event, List<SecurityEvent> history) {
                if (event.getEventType() != SecurityEventType.EXPORT) { return Optional.empty(); }
                long total = 0;
                Instant start = event.getOccurredAt().atZone(ZoneOffset.UTC).truncatedTo(ChronoUnit.DAYS).toInstant();
                for (SecurityEvent candidate : history) {
                    if (!candidate.getOccurredAt().isBefore(start) && sameScope(event, candidate, Scope.USER)
                        && candidate.getEventType() == SecurityEventType.EXPORT) { total += candidate.getDataCount(); }
                }
                return total >= 10000 || atLeast(event.getAttribute("baseline_ratio"), 3.0d)
                    ? match(event, RiskLevel.HIGH, actions(ControlActionType.DENY), "daily export exceeds baseline")
                    : Optional.<RuleMatch>empty();
            }
        };
    }

    private static DetectionRule privilegeOne() {
        return new BaseRule("PRIV-01") {
            @Override public Optional<RuleMatch> evaluate(SecurityEvent event, List<SecurityEvent> history) {
                boolean selfGrant = event.getUserId() != null && event.getUserId().equals(event.getAttribute("target_user_id"));
                return event.getEventType() == SecurityEventType.ROLE_GRANT && selfGrant && truthy(event.getAttribute("privilege_increase"))
                    ? match(event, RiskLevel.HIGH, actions(ControlActionType.DENY), "self privilege escalation") : Optional.<RuleMatch>empty();
            }
        };
    }

    private static DetectionRule privilegeTwo() {
        return new BaseRule("PRIV-02") {
            @Override public Optional<RuleMatch> evaluate(SecurityEvent event, List<SecurityEvent> history) {
                boolean match = event.getEventType() == SecurityEventType.ADMIN_CREATE
                    || (event.getEventType() == SecurityEventType.ROLE_GRANT && truthy(event.getAttribute("high_privilege")));
                return match ? match(event, RiskLevel.HIGH, actions(ControlActionType.REQUIRE_MFA, ControlActionType.REQUIRE_APPROVAL), "administrator privilege change")
                    : Optional.<RuleMatch>empty();
            }
        };
    }

    private static DetectionRule securityOne() {
        return new BaseRule("SECU-01") {
            @Override public Optional<RuleMatch> evaluate(SecurityEvent event, List<SecurityEvent> history) {
                boolean match = event.getEventType() == SecurityEventType.RULE_CHANGE
                    || event.getEventType() == SecurityEventType.AUDIT_CONFIG_CHANGE
                    || event.getEventType() == SecurityEventType.SECURITY_SWITCH_CHANGE;
                return match ? match(event, RiskLevel.HIGH, actions(ControlActionType.RECORD), "security configuration changed")
                    : Optional.<RuleMatch>empty();
            }
        };
    }

    private static Optional<RuleMatch> match(SecurityEvent event, RiskLevel level, List<ControlActionType> actions, String reason) {
        String resource = event.getResourceType() == null ? "" : event.getResourceType() + ":" + nullToEmpty(event.getResourceId());
        return Optional.of(new RuleMatch("", level, event.subject(), resource, reason, actions));
    }

    private static abstract class BaseRule implements DetectionRule {
        private final String id;
        private BaseRule(String id) { this.id = id; }
        @Override public String getRuleId() { return id; }
        protected Optional<RuleMatch> match(SecurityEvent event, RiskLevel level, List<ControlActionType> actions, String reason) {
            return match(event, level, actions, reason, event.subject(), Duration.ofMinutes(15));
        }
        protected Optional<RuleMatch> match(SecurityEvent event, RiskLevel level, List<ControlActionType> actions,
                                            String reason, String subject, Duration controlTtl) {
            String resource = event.getResourceType() == null ? "" : event.getResourceType() + ":" + nullToEmpty(event.getResourceId());
            return Optional.of(new RuleMatch(id, level, subject, resource, reason, actions, controlTtl));
        }
    }

    private static List<SecurityEvent> recent(SecurityEvent event, List<SecurityEvent> history, Duration window) {
        Instant start = event.getOccurredAt().minus(window);
        List<SecurityEvent> result = new ArrayList<SecurityEvent>();
        for (SecurityEvent candidate : history) {
            if (!candidate.getOccurredAt().isBefore(start) && !candidate.getOccurredAt().isAfter(event.getOccurredAt())) { result.add(candidate); }
        }
        return result;
    }
    private static boolean sameScope(SecurityEvent left, SecurityEvent right, Scope scope) {
        if (scope == Scope.USER) { return left.subject().equals(right.subject()); }
        String leftSession = left.getSessionIdHash(); String rightSession = right.getSessionIdHash();
        return leftSession != null && leftSession.equals(rightSession) || (leftSession == null && left.subject().equals(right.subject()));
    }
    private static List<ControlActionType> actions(ControlActionType... values) { return Arrays.asList(values); }
    private static boolean truthy(String value) { return "true".equalsIgnoreCase(value) || "1".equals(value); }
    private static boolean atLeast(String value, double threshold) { try { return value != null && Double.parseDouble(value) >= threshold; } catch (NumberFormatException ignored) { return false; } }
    private static String nullToEmpty(String value) { return value == null ? "" : value; }
    private enum Scope { USER, SESSION_OR_USER }
}

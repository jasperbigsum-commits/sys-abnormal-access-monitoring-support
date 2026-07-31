package io.github.jasper.monitoring.core;

import io.github.jasper.monitoring.core.domain.rule.DefaultRuleCatalog;
import io.github.jasper.monitoring.core.domain.rule.DetectionRule;
import io.github.jasper.monitoring.api.control.ControlType;
import io.github.jasper.monitoring.api.rule.RuleCatalog;
import io.github.jasper.monitoring.api.rule.RuleMode;
import io.github.jasper.monitoring.api.rule.RuleType;
import io.github.jasper.monitoring.api.rule.RuleDefinition;
import io.github.jasper.monitoring.api.rule.RuleSource;
import io.github.jasper.monitoring.api.RiskLevel;
import io.github.jasper.monitoring.api.ControlActionType;
import io.github.jasper.monitoring.api.action.ActionDisposition;
import io.github.jasper.monitoring.api.action.ActionRequirement;
import io.github.jasper.monitoring.api.action.BuiltInActions;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DefaultRuleCatalogCoverageTest {
    @Test
    void exportRulesDeclareCurrentAttemptBlockingWithoutUsingDenyAsTheDecision() {
        RuleDefinition<?> exptOne = definition("EXPT-01");
        RuleDefinition<?> exptTwo = definition("EXPT-02");

        assertEquals(ActionDisposition.BLOCK, exptOne.getDisposition());
        assertTrue(exptOne.getRequirements().contains(ActionRequirement.APPROVAL));
        assertFalse(exptOne.getControls().contains(ControlActionType.DENY));
        assertEquals(ActionDisposition.BLOCK, exptTwo.getDisposition());
        assertFalse(exptTwo.getControls().contains(ControlActionType.DENY));
    }

    private static RuleDefinition<?> definition(String id) {
        for (DetectionRule<?> rule : DefaultRuleCatalog.typedRules()) {
            if (id.equals(rule.getRuleId())) return rule.definition();
        }
        throw new AssertionError("Missing rule " + id);
    }

    @Test
    void includesEveryInitialRuleFromTheConstructionBaseline() {
        Set<String> ruleIds = new HashSet<String>();
        for (DetectionRule rule : DefaultRuleCatalog.typedRules()) {
            ruleIds.add(rule.getRuleId());
        }

        assertEquals(14, ruleIds.size());
        assertEquals(new HashSet<String>(java.util.Arrays.asList(
            "AUTH-01", "AUTH-02", "AUTH-03", "SESS-01", "AUTHZ-01", "AUTHZ-02", "DATA-01",
            "DATA-02", "DATA-03", "EXPT-01", "EXPT-02", "PRIV-01", "PRIV-02", "SECU-01")), ruleIds);
    }

    @Test
    void exposesEveryExecutableControlEmittedByBuiltInRules() {
        assertEquals(java.util.EnumSet.of(ControlType.REQUIRE_CAPTCHA, ControlType.RATE_LIMIT,
            ControlType.REVOKE_SESSION, ControlType.REQUIRE_MFA, ControlType.DENY,
            ControlType.REQUIRE_APPROVAL), DefaultRuleCatalog.requiredControlTypes());
    }

    @Test
    void frozenCatalogDerivesControlsOnlyFromEnforceRules() {
        RuleCatalog catalog = new RuleCatalog();
        catalog.register(definition(EnforceRule.class, "ENFORCE", RuleMode.ENFORCE,
            ControlActionType.DENY, ControlActionType.RECORD));
        catalog.register(definition(AlertRule.class, "ALERT", RuleMode.ALERT_ONLY,
            ControlActionType.REQUIRE_MFA));
        catalog.register(definition(DisabledRule.class, "DISABLED", RuleMode.DISABLED,
            ControlActionType.REQUIRE_APPROVAL));
        catalog.freeze();

        assertEquals(java.util.EnumSet.of(ControlType.DENY), catalog.requiredControlTypes());
    }

    private static <R extends RuleType> RuleDefinition<R> definition(Class<R> type, String id,
            RuleMode mode, ControlActionType... controls) {
        RuleDefinition.Builder<R> builder = RuleDefinition.builder(type, id)
            .appliesTo(BuiltInActions.LoginFailure.class)
            .historyWindow(java.time.Duration.ZERO).threshold(1).risk(RiskLevel.HIGH)
            .mode(mode).source(RuleSource.INTERNAL);
        for (ControlActionType control : controls) builder.control(control);
        return builder.build();
    }

    static final class EnforceRule implements RuleType { }
    static final class AlertRule implements RuleType { }
    static final class DisabledRule implements RuleType { }
}

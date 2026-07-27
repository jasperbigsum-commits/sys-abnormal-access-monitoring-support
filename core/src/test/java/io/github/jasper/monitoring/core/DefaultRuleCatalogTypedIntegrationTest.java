package io.github.jasper.monitoring.core;

import io.github.jasper.monitoring.api.rule.RuleCatalog;
import io.github.jasper.monitoring.api.rule.RuleMode;
import io.github.jasper.monitoring.api.SecurityEventType;
import io.github.jasper.monitoring.api.action.ActionDefinition;
import io.github.jasper.monitoring.api.action.ActionFailurePolicy;
import io.github.jasper.monitoring.api.action.BuiltInActions;
import io.github.jasper.monitoring.core.domain.SecurityEvent;
import io.github.jasper.monitoring.core.domain.rule.RuleEvaluationContext;
import java.time.Instant;
import io.github.jasper.monitoring.core.domain.rule.DefaultRuleCatalog;
import io.github.jasper.monitoring.core.domain.rule.DetectionRule;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

class DefaultRuleCatalogTypedIntegrationTest {
    @Test
    void exposesEveryBuiltInRuleThroughFrozenTypedCatalog() {
        RuleCatalog catalog = DefaultRuleCatalog.typedCatalog();

        assertTrue(catalog.isFrozen());
        assertEquals(14, catalog.asMap().size());
        assertEquals(14, DefaultRuleCatalog.typedRules().size());
        for (DetectionRule<?> rule : DefaultRuleCatalog.typedRules()) {
            assertEquals(rule.definition().getId(), rule.getRuleId());
            assertEquals(RuleMode.ENFORCE, rule.definition().getMode(),
                "Built-in rules must use the global mode as the deployment safety ceiling");
            assertTrue(catalog.asMap().containsKey(rule.type()));
        }
    }

    @Test
    void evaluatesExportRuleOnlyForExportActionContext() {
        DetectionRule<?> exportRule = DefaultRuleCatalog.typedRules().get(9);
        SecurityEvent event = SecurityEvent.builder().eventType(SecurityEventType.EXPORT)
            .occurredAt(Instant.parse("2026-07-26T00:00:00Z")).sourceIp("203.0.113.8")
            .dataCount(6000L).build();
        ActionDefinition reportExport = ActionDefinition.builder("report:export")
            .eventType(SecurityEventType.EXPORT).resourceType("report")
            .failurePolicy(ActionFailurePolicy.FAIL_CLOSED).build();
        ActionDefinition sensitiveView = ActionDefinition.builder("resource:view-sensitive")
            .eventType(SecurityEventType.VIEW_SENSITIVE).resourceType("resource")
            .failurePolicy(ActionFailurePolicy.OBSERVE_ONLY).build();

        RuleEvaluationContext.Evaluation export = RuleEvaluationContext.builder(
                event, BuiltInActions.ReportExport.class, reportExport).build().evaluate(exportRule);
        RuleEvaluationContext.Evaluation unrelated = RuleEvaluationContext.builder(
                event, BuiltInActions.SensitiveView.class, sensitiveView).build().evaluate(exportRule);

        assertEquals(RuleEvaluationContext.Status.EVALUATED, export.getStatus());
        assertTrue(export.getMatch().isPresent());
        assertEquals(RuleEvaluationContext.Status.SKIPPED_NOT_APPLICABLE, unrelated.getStatus());
    }

    @Test
    void frontendSupplementalSignalIsNotEligibleForAnyBuiltInRule() {
        io.github.jasper.monitoring.api.action.ActionCatalog actions =
            new io.github.jasper.monitoring.api.action.ActionCatalog();
        BuiltInActions.registerInto(actions);
        actions.freeze();
        ActionDefinition frontendSignal = actions.require(BuiltInActions.FrontendSignal.class);
        SecurityEvent event = SecurityEvent.builder().eventType(SecurityEventType.QUERY)
            .occurredAt(Instant.parse("2026-07-26T00:00:00Z")).sourceIp("203.0.113.8").build();

        for (DetectionRule<?> rule : DefaultRuleCatalog.typedRules()) {
            RuleEvaluationContext.Evaluation result = RuleEvaluationContext.builder(
                event, BuiltInActions.FrontendSignal.class, frontendSignal).build().evaluate(rule);
            assertEquals(RuleEvaluationContext.Status.SKIPPED_NOT_APPLICABLE, result.getStatus(),
                rule.getRuleId());
        }
    }
}

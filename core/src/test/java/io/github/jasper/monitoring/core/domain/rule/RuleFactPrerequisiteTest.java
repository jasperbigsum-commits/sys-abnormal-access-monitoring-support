package io.github.jasper.monitoring.core.domain.rule;

import io.github.jasper.monitoring.api.RiskLevel;
import io.github.jasper.monitoring.api.SecurityEventType;
import io.github.jasper.monitoring.api.action.ActionDefinition;
import io.github.jasper.monitoring.api.action.ActionFailurePolicy;
import io.github.jasper.monitoring.api.action.ActionType;
import io.github.jasper.monitoring.api.fact.ActionFacts;
import io.github.jasper.monitoring.api.fact.FactSource;
import io.github.jasper.monitoring.api.fact.FactType;
import io.github.jasper.monitoring.api.rule.RuleDefinition;
import io.github.jasper.monitoring.api.rule.RuleMode;
import io.github.jasper.monitoring.api.rule.RuleSource;
import io.github.jasper.monitoring.api.rule.RuleType;
import io.github.jasper.monitoring.core.domain.RuleMatch;
import io.github.jasper.monitoring.core.domain.SecurityEvent;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static io.github.jasper.monitoring.core.domain.rule.RuleEvaluationContext.Status.EVALUATED;
import static io.github.jasper.monitoring.core.domain.rule.RuleEvaluationContext.Status.SKIPPED_MISSING_FACT;
import static org.junit.jupiter.api.Assertions.assertEquals;

class RuleFactPrerequisiteTest {

    @Test
    void missingFactSkipsOnlyRulesThatDeclareThatFact() {
        ActionDefinition action = ActionDefinition.builder("report:export")
            .eventType(SecurityEventType.EXPORT)
            .resourceType("report")
            .participateIn(LargeExportRule.class)
            .participateIn(LoginVelocityRule.class)
            .require(DataCountFact.class, FactSource.METHOD_PARAMETER, FactSource.HOST_PROVIDER)
            .failurePolicy(ActionFailurePolicy.FAIL_CLOSED)
            .build();
        RuleEvaluationContext context = RuleEvaluationContext.builder(
                event(), ReportExport.class, action)
            .facts(ActionFacts.builder().build())
            .history(Collections.<SecurityEvent>emptyList())
            .build();
        CountingRule<LargeExportRule> export = new CountingRule<LargeExportRule>(
            definition(LargeExportRule.class, "EXPT-01", DataCountFact.class));
        CountingRule<LoginVelocityRule> login = new CountingRule<LoginVelocityRule>(
            definition(LoginVelocityRule.class, "AUTH-01", null));

        RuleEvaluationContext.Evaluation exportEvaluation = context.evaluate(export);
        RuleEvaluationContext.Evaluation loginEvaluation = context.evaluate(login);

        assertEquals(SKIPPED_MISSING_FACT, exportEvaluation.getStatus());
        assertEquals("RULE_FACT_MISSING", exportEvaluation.getDiagnosticCode());
        assertEquals(0, export.evaluations);
        assertEquals(EVALUATED, loginEvaluation.getStatus());
        assertEquals(1, login.evaluations);
    }

    @Test
    void factFromUndeclaredSourceIsNotAcceptedAsACompletedPrerequisite() {
        ActionDefinition action = ActionDefinition.builder("report:export")
            .eventType(SecurityEventType.EXPORT)
            .resourceType("report")
            .participateIn(LargeExportRule.class)
            .require(DataCountFact.class, FactSource.METHOD_PARAMETER, FactSource.HOST_PROVIDER)
            .failurePolicy(ActionFailurePolicy.FAIL_CLOSED)
            .build();
        RuleEvaluationContext context = RuleEvaluationContext.builder(
                event(), ReportExport.class, action)
            .facts(ActionFacts.builder().put(DataCountFact.class, 6000L).build())
            .factSource(DataCountFact.class, FactSource.CLIENT_SUPPLEMENTAL)
            .build();
        CountingRule<LargeExportRule> export = new CountingRule<LargeExportRule>(
            definition(LargeExportRule.class, "EXPT-01", DataCountFact.class));

        RuleEvaluationContext.Evaluation evaluation = context.evaluate(export);

        assertEquals(SKIPPED_MISSING_FACT, evaluation.getStatus());
        assertEquals("RULE_FACT_SOURCE_NOT_ACCEPTED", evaluation.getDiagnosticCode());
        assertEquals(0, export.evaluations);
    }

    private static <R extends RuleType> RuleDefinition<R> definition(Class<R> type, String id,
            Class<? extends FactType<?>> requiredFact) {
        RuleDefinition.Builder<R> builder = RuleDefinition.builder(type, id)
            .appliesTo(ReportExport.class)
            .historyWindow(Duration.ofMinutes(5))
            .threshold(1L)
            .risk(RiskLevel.MEDIUM)
            .mode(RuleMode.OBSERVE)
            .source(RuleSource.INTERNAL);
        if (requiredFact != null) {
            builder.require(requiredFact, FactSource.METHOD_PARAMETER, FactSource.HOST_PROVIDER);
        }
        return builder.build();
    }

    private static SecurityEvent event() {
        return SecurityEvent.builder()
            .eventType(SecurityEventType.EXPORT)
            .occurredAt(Instant.parse("2026-07-26T00:00:00Z"))
            .sourceIp("203.0.113.8")
            .build();
    }

    static final class ReportExport implements ActionType {
    }

    static final class DataCountFact implements FactType<Long> {
    }

    static final class LargeExportRule implements RuleType {
    }

    static final class LoginVelocityRule implements RuleType {
    }

    private static final class CountingRule<R extends RuleType> implements DetectionRule<R> {
        private final RuleDefinition<R> definition;
        private int evaluations;

        private CountingRule(RuleDefinition<R> definition) {
            this.definition = definition;
        }

        @Override
        public RuleDefinition<R> definition() {
            return definition;
        }

        @Override
        public Optional<RuleMatch> evaluate(RuleEvaluationContext context) {
            evaluations++;
            return Optional.empty();
        }
    }
}

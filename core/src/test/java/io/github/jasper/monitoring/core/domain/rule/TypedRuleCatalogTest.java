package io.github.jasper.monitoring.core.domain.rule;

import io.github.jasper.monitoring.api.ControlActionType;
import io.github.jasper.monitoring.api.RiskLevel;
import io.github.jasper.monitoring.api.action.ActionContract;
import io.github.jasper.monitoring.api.action.ActionType;
import io.github.jasper.monitoring.api.error.MonitoringConfigurationException;
import io.github.jasper.monitoring.api.fact.FactSource;
import io.github.jasper.monitoring.api.fact.FactType;
import io.github.jasper.monitoring.api.rule.RuleCatalog;
import io.github.jasper.monitoring.api.rule.RuleDefinition;
import io.github.jasper.monitoring.api.rule.RuleMode;
import io.github.jasper.monitoring.api.rule.RuleSource;
import io.github.jasper.monitoring.api.rule.RuleType;
import java.time.Duration;
import java.util.Collections;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TypedRuleCatalogTest {

    @Test
    void freezesTypedDefinitionsWithExplicitActionAndFactSemantics() {
        RuleDefinition<LargeExportRule> definition = RuleDefinition.builder(
                LargeExportRule.class, "EXPT-01")
            .appliesTo(ReportExport.class)
            .appliesToContract(ExportContract.class)
            .require(DataCountFact.class, FactSource.METHOD_PARAMETER, FactSource.HOST_PROVIDER)
            .historyWindow(Duration.ofMinutes(30))
            .threshold(5000L)
            .risk(RiskLevel.HIGH)
            .control(ControlActionType.DENY)
            .mode(RuleMode.ENFORCE)
            .source(RuleSource.INTERNAL)
            .build();
        RuleCatalog catalog = new RuleCatalog();
        catalog.register(definition);
        catalog.freeze();

        RuleDefinition<LargeExportRule> registered = catalog.require(LargeExportRule.class);

        assertEquals("EXPT-01", registered.getId());
        assertEquals(Duration.ofMinutes(30), registered.getHistoryWindow());
        assertEquals(5000L, registered.getThreshold());
        assertEquals(Collections.singleton(ControlActionType.DENY), registered.getControls());
        assertTrue(registered.getActionTypes().contains(ReportExport.class));
        assertTrue(registered.getActionContracts().contains(ExportContract.class));
        assertEquals(Collections.singleton(FactSource.HOST_PROVIDER),
            intersection(registered.getAcceptedSources(DataCountFact.class),
                Collections.singleton(FactSource.HOST_PROVIDER)));
    }

    @Test
    void rejectsDuplicateRuleTypeOrStableIdAndRegistrationAfterFreeze() {
        RuleCatalog catalog = new RuleCatalog();
        catalog.register(definition(LargeExportRule.class, "EXPT-01"));

        assertThrows(MonitoringConfigurationException.class,
            () -> catalog.register(definition(LargeExportRule.class, "EXPT-02")));
        assertThrows(MonitoringConfigurationException.class,
            () -> catalog.register(definition(LoginVelocityRule.class, "EXPT-01")));

        catalog.freeze();

        assertThrows(MonitoringConfigurationException.class,
            () -> catalog.register(definition(LoginVelocityRule.class, "AUTH-01")));
        assertThrows(UnsupportedOperationException.class, () -> catalog.asMap().clear());
    }

    private static <R extends RuleType> RuleDefinition<R> definition(Class<R> type, String id) {
        return RuleDefinition.builder(type, id)
            .appliesTo(ReportExport.class)
            .historyWindow(Duration.ZERO)
            .threshold(1L)
            .risk(RiskLevel.MEDIUM)
            .mode(RuleMode.OBSERVE)
            .source(RuleSource.INTERNAL)
            .build();
    }

    private static <T> java.util.Set<T> intersection(java.util.Set<T> left,
            java.util.Set<T> right) {
        java.util.Set<T> result = new java.util.LinkedHashSet<T>(left);
        result.retainAll(right);
        return result;
    }

    interface ExportContract extends ActionContract {
    }

    static final class ReportExport implements ActionType, ExportContract {
    }

    static final class DataCountFact implements FactType<Long> {
    }

    static final class LargeExportRule implements RuleType {
    }

    static final class LoginVelocityRule implements RuleType {
    }
}

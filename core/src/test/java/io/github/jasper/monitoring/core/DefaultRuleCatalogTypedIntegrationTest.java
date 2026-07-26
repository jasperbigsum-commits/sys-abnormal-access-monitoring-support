package io.github.jasper.monitoring.core;

import io.github.jasper.monitoring.api.rule.RuleCatalog;
import io.github.jasper.monitoring.core.domain.rule.DefaultRuleCatalog;
import io.github.jasper.monitoring.core.domain.rule.DetectionRule;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultRuleCatalogTypedIntegrationTest {
    @Test
    void exposesAllLegacyBaselineRulesThroughFrozenTypedCatalog() {
        RuleCatalog catalog = DefaultRuleCatalog.typedCatalog();

        assertTrue(catalog.isFrozen());
        assertEquals(14, catalog.asMap().size());
        assertEquals(14, DefaultRuleCatalog.typedRules().size());
        for (DetectionRule<?> rule : DefaultRuleCatalog.typedRules()) {
            assertEquals(rule.definition().getId(), rule.getRuleId());
            assertTrue(catalog.asMap().containsKey(rule.type()));
        }
    }
}

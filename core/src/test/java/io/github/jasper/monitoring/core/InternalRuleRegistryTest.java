package io.github.jasper.monitoring.core;

import io.github.jasper.monitoring.core.domain.SecurityEvent;
import io.github.jasper.monitoring.core.application.rule.InternalRuleRegistry;
import io.github.jasper.monitoring.core.domain.RuleMatch;
import io.github.jasper.monitoring.core.domain.rule.DetectionRule;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Collections;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class InternalRuleRegistryTest {

    @Test
    void freezesInternalRulesAndExposesThemAsImmutableManagementEntries() {
        InternalRuleRegistry registry = new InternalRuleRegistry();
        registry.register(new TestRule("HOST-01"));

        registry.freeze();

        assertTrue(registry.isFrozen());
        assertEquals(1, registry.rules().size());
        assertEquals("HOST-01", registry.entries().get(0).getRuleId());
        assertFalse(registry.entries().get(0).isMutable());
        assertThrows(IllegalStateException.class, () -> registry.register(new TestRule("HOST-02")));
    }

    private static final class TestRule implements DetectionRule {
        private final String ruleId;

        private TestRule(String ruleId) {
            this.ruleId = ruleId;
        }

        @Override
        public String getRuleId() {
            return ruleId;
        }

        @Override
        public Optional<RuleMatch> evaluate(SecurityEvent event, java.util.List<SecurityEvent> history) {
            return Optional.empty();
        }
    }
}

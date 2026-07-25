package io.github.jasper.monitoring.spring.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.jasper.monitoring.api.EventFactSource;
import io.github.jasper.monitoring.api.MonitorActionDefinition;
import io.github.jasper.monitoring.api.MonitorActionFacts;
import java.lang.reflect.Method;
import java.util.Collections;
import org.junit.jupiter.api.Test;

class AnnotatedActionFactsTest {

    @Test
    void blocksCanonicalStaticAndReservedDynamicAttributeOverrides() throws Exception {
        MonitorActionDefinition definition = MonitorActionDefinition.builder("report:export")
            .attribute("classification", "static")
            .ruleTag("approved")
            .build();
        AnnotatedActionFacts facts = new AnnotatedActionFacts(definition,
            Fixture.class.getDeclaredMethod("export"), Collections.emptyList());

        facts.merge(MonitorActionFacts.builder()
            .attribute("Classification", "dynamic")
            .attribute("MONITOR.RULE-TAG.injected", "false")
            .build(), EventFactSource.EVENT_ENRICHER);

        assertEquals("static", facts.getDefinition().getAttributes().get("classification"));
        assertFalse(facts.snapshot().getAttributes().containsKey("classification"));
        assertFalse(facts.snapshot().getAttributes().containsKey("monitor.rule-tag.injected"));
        assertEquals(1, facts.getInputIssues().size());
        assertTrue(facts.getInputIssues().stream().allMatch(issue ->
            "PROTECTED_FACT_OVERRIDE".equals(issue.getIssueCode())));
        assertTrue(facts.getInputIssues().stream().allMatch(issue ->
            EventFactSource.EVENT_ENRICHER.name().equals(issue.getSourceType())));
    }

    private static final class Fixture {
        @SuppressWarnings("unused")
        private void export() {
        }
    }
}

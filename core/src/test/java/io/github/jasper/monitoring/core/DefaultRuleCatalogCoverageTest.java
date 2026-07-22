package io.github.jasper.monitoring.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DefaultRuleCatalogCoverageTest {

    @Test
    void includesEveryInitialRuleFromTheConstructionBaseline() {
        Set<String> ruleIds = new HashSet<String>();
        for (DetectionRule rule : DefaultRuleCatalog.initialRules()) {
            ruleIds.add(rule.getRuleId());
        }

        assertEquals(14, ruleIds.size());
        assertEquals(new HashSet<String>(java.util.Arrays.asList(
            "AUTH-01", "AUTH-02", "AUTH-03", "SESS-01", "AUTHZ-01", "AUTHZ-02", "DATA-01",
            "DATA-02", "DATA-03", "EXPT-01", "EXPT-02", "PRIV-01", "PRIV-02", "SECU-01")), ruleIds);
    }
}

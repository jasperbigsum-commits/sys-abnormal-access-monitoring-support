package io.github.jasper.monitoring.api.rule;

import io.github.jasper.monitoring.api.ControlActionType;
import io.github.jasper.monitoring.api.RiskLevel;
import io.github.jasper.monitoring.api.action.ActionDisposition;
import io.github.jasper.monitoring.api.action.ActionRequirement;
import io.github.jasper.monitoring.api.action.ActionType;
import java.time.Duration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuleDecisionModelTest {
    @Test
    void keepsCurrentDispositionRequirementsAndFutureControlsIndependent() {
        RuleDefinition<TestRule> definition = RuleDefinition.builder(TestRule.class, "TEST-01")
            .appliesTo(TestAction.class)
            .historyWindow(Duration.ZERO)
            .threshold(1L)
            .risk(RiskLevel.HIGH)
            .disposition(ActionDisposition.BLOCK)
            .requirement(ActionRequirement.APPROVAL)
            .control(ControlActionType.REVOKE_SESSION)
            .mode(RuleMode.ENFORCE)
            .source(RuleSource.INTERNAL)
            .build();

        assertEquals(ActionDisposition.BLOCK, definition.getDisposition());
        assertTrue(definition.getRequirements().contains(ActionRequirement.APPROVAL));
        assertTrue(definition.getControls().contains(ControlActionType.REVOKE_SESSION));
    }

    static final class TestAction implements ActionType { }
    static final class TestRule implements RuleType { }
}

package io.github.jasper.monitoring.api.action;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.jasper.monitoring.api.fact.BuiltInFacts;
import io.github.jasper.monitoring.api.fact.StaticActionFact;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

class TypedActionContractTest {

    @Test
    void exposesTheConcreteActionTypeAtRuntime() throws Exception {
        Method method = DeclaredActions.class.getDeclaredMethod("exportReport");

        MonitorAction annotation = method.getAnnotation(MonitorAction.class);

        assertEquals(ReportExportAction.class, annotation.value());
        assertTrue(ActionType.class.isAssignableFrom(annotation.value()));
        assertTrue(ActionContract.class.isAssignableFrom(ReportExportAction.class));
    }

    @Test
    void exposesRepeatableStaticFactsAtRuntime() throws Exception {
        Method method = DeclaredActions.class.getDeclaredMethod("exportReport");

        StaticActionFact[] facts = method.getAnnotationsByType(StaticActionFact.class);

        assertEquals(2, facts.length);
        assertEquals(BuiltInFacts.ResourceId.class, facts[0].fact());
        assertEquals("fixed-report", facts[0].value());
        assertEquals(BuiltInFacts.DataCount.class, facts[1].fact());
        assertEquals("100", facts[1].value());
    }

    private static final class DeclaredActions {
        @MonitorAction(ReportExportAction.class)
        @StaticActionFact(fact = BuiltInFacts.ResourceId.class, value = "fixed-report")
        @StaticActionFact(fact = BuiltInFacts.DataCount.class, value = "100")
        void exportReport() {
        }
    }

    private static final class ReportExportAction implements ActionType, ActionContract {
    }
}

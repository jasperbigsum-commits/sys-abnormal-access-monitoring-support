package io.github.jasper.monitoring.api.action;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    private static final class DeclaredActions {
        @MonitorAction(ReportExportAction.class)
        void exportReport() {
        }
    }

    private static final class ReportExportAction implements ActionType, ActionContract {
    }
}

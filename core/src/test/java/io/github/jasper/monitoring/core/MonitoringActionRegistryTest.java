package io.github.jasper.monitoring.core;

import io.github.jasper.monitoring.api.error.MonitoringConfigurationException;
import io.github.jasper.monitoring.api.error.MonitoringErrorCode;
import io.github.jasper.monitoring.api.error.MonitoringValidationException;
import io.github.jasper.monitoring.core.application.MonitoringActionRegistry;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import io.github.jasper.monitoring.api.MonitorActionDefinition;
import io.github.jasper.monitoring.api.SecurityEventType;
import org.junit.jupiter.api.Test;

class MonitoringActionRegistryTest {

    @Test
    void keepsOneStableDefinitionForEachActionCode() {
        MonitoringActionRegistry registry = new MonitoringActionRegistry();
        MonitorActionDefinition definition = MonitorActionDefinition.builder("report:export")
            .eventType(SecurityEventType.EXPORT).build();

        registry.register(definition).register(definition);

        assertEquals(definition, registry.require("report:export"));
        assertEquals(1, registry.snapshot().size());
        MonitoringConfigurationException exception = assertThrows(MonitoringConfigurationException.class,
            () -> registry.register(
                MonitorActionDefinition.builder("report:export").eventType(SecurityEventType.QUERY).build()));
        assertEquals(MonitoringErrorCode.CONFLICTING_ACTION_DEFINITION, exception.getErrorCode());
    }

    @Test
    void rejectsUnknownActionCodeInsteadOfSilentlyChangingTheAuditDimension() {
        MonitoringActionRegistry registry = new MonitoringActionRegistry();

        MonitoringValidationException exception = assertThrows(MonitoringValidationException.class,
            () -> registry.require("report:export"));
        assertEquals(MonitoringErrorCode.ACTION_NOT_REGISTERED, exception.getErrorCode());
    }
}

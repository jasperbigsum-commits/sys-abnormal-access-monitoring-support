package io.github.jasper.monitoring.api;

import io.github.jasper.monitoring.api.error.MonitoringConfigurationException;
import io.github.jasper.monitoring.api.error.MonitoringErrorCode;
import io.github.jasper.monitoring.api.error.MonitoringFailure;
import io.github.jasper.monitoring.api.error.MonitoringPersistenceException;
import io.github.jasper.monitoring.api.error.MonitoringStateException;
import io.github.jasper.monitoring.api.error.MonitoringValidationException;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class MonitoringErrorContractTest {
    @Test
    void exposesStableCodesAndPreservesLegacyValidationAndStateCatchTypes() {
        RuntimeException cause = new RuntimeException("database unavailable");
        MonitoringValidationException validation = new MonitoringValidationException(
            MonitoringErrorCode.REQUIRED_FIELD_MISSING, "action is required");
        MonitoringConfigurationException configuration = new MonitoringConfigurationException(
            MonitoringErrorCode.ENFORCEMENT_HANDLER_REQUIRED, "ENFORCE requires a handler");
        MonitoringStateException state = new MonitoringStateException(
            MonitoringErrorCode.INVALID_ALERT_TRANSITION, "alert is already closed");
        MonitoringPersistenceException persistence = new MonitoringPersistenceException(
            MonitoringErrorCode.PERSISTENCE_OPERATION_FAILED, "Monitoring persistence failed", cause);

        assertEquals("MON-001", validation.getErrorCode().getCode());
        assertTrue(validation instanceof IllegalArgumentException);
        assertTrue(configuration instanceof IllegalStateException);
        assertTrue(state instanceof IllegalStateException);
        assertEquals(MonitoringErrorCode.PERSISTENCE_OPERATION_FAILED,
            ((MonitoringFailure) persistence).getErrorCode());
        assertSame(cause, persistence.getCause());
    }
}

package io.github.jasper.monitoring.web;

import io.github.jasper.monitoring.api.error.MonitoringErrorCode;
import io.github.jasper.monitoring.api.error.MonitoringValidationException;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class FrontendSignalValidatorTest {

    @Test
    void rejectsSignalsOutsideAllowedClockSkew() {
        FrontendSignal signal = FrontendSignal.builder()
            .clientEventId("client-1")
            .occurredAt(Instant.parse("2026-07-22T00:20:00Z"))
            .requestId("request-1")
            .route("/orders")
            .action("VIEW")
            .build();

        MonitoringValidationException exception = assertThrows(MonitoringValidationException.class,
            () -> new FrontendSignalValidator(Duration.ofMinutes(5))
                .validate(signal, Instant.parse("2026-07-22T00:00:00Z")));

        assertEquals(MonitoringErrorCode.INVALID_FIELD_VALUE, exception.getErrorCode());
    }
}

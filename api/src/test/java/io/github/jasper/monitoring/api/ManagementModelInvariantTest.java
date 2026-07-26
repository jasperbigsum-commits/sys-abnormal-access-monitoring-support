package io.github.jasper.monitoring.api;

import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.jasper.monitoring.api.management.model.AlertSummary;
import io.github.jasper.monitoring.api.management.model.ControlAttemptView;
import io.github.jasper.monitoring.api.management.model.ControlDetails;
import io.github.jasper.monitoring.api.management.model.SecurityEventSummary;
import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.Test;

class ManagementModelInvariantTest {
    @Test
    void rejectsInvalidAlertSummary() {
        assertThrows(IllegalArgumentException.class, () -> new AlertSummary("", "scope", "OPEN", 1));
        assertThrows(IllegalArgumentException.class, () -> new AlertSummary("id", "scope", "UNKNOWN", 1));
        assertThrows(IllegalArgumentException.class, () -> new AlertSummary("id", "scope", "OPEN", 0));
    }

    @Test
    void rejectsInvalidSecurityEventSummary() {
        assertThrows(IllegalArgumentException.class,
            () -> new SecurityEventSummary("id", "scope", "bad code!", "VALID", 1));
        assertThrows(IllegalArgumentException.class,
            () -> new SecurityEventSummary("id", "scope", "data:query", "", 1));
        assertThrows(IllegalArgumentException.class,
            () -> new SecurityEventSummary("id", "scope", "data:query", "VALID", 0));
    }

    @Test
    void rejectsInvalidControlAttempts() {
        assertThrows(IllegalArgumentException.class,
            () -> new ControlDetails("id", "scope", "PENDING", 1, null));
        assertThrows(IllegalArgumentException.class,
            () -> new ControlDetails("id", "scope", "PENDING", 1,
                Arrays.asList(new ControlAttemptView(2, "PENDING"), new ControlAttemptView(1, "FAILED"))));
        new ControlDetails("id", "scope", "PENDING", 1, Collections.<ControlAttemptView>emptyList());
    }
}

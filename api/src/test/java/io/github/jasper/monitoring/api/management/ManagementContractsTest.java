package io.github.jasper.monitoring.api.management;

import io.github.jasper.monitoring.api.management.command.AlertAcknowledgeCommand;
import io.github.jasper.monitoring.api.management.model.AlertView;
import io.github.jasper.monitoring.api.management.query.AlertQuery;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

class ManagementContractsTest {
    @Test
    void rejectsInvalidPageRequest() {
        assertThrows(IllegalArgumentException.class, () -> ManagementPageRequest.of(0, 201, "createdAt"));
        assertThrows(IllegalArgumentException.class, () -> ManagementPageRequest.of(-1, 20, "createdAt"));
    }

    @Test
    void pageIsImmutable() {
        ManagementPage<AlertView> page = ManagementPage.of(Collections.singletonList(AlertView.of("a", "sys", 1L)), 0, 20, 1);
        assertThrows(UnsupportedOperationException.class, () -> page.getItems().clear());
    }

    @Test
    void actorAndAuthorizerAreExplicitServiceBoundary() {
        assertThrows(NullPointerException.class, () -> ManagementActor.of(null, "host"));
        assertThrows(NullPointerException.class, () -> ManagementAuthorizer.requireArguments(null, null, null));
    }

    @Test
    void commandsCarryExpectedVersionAndReason() {
        AlertAcknowledgeCommand command = AlertAcknowledgeCommand.of("a", 3L, "triaged");
        assertEquals(3L, command.getExpectedVersion());
        assertEquals("triaged", command.getReason());
        assertThrows(IllegalArgumentException.class, () -> AlertAcknowledgeCommand.of("a", 0L, "x"));
    }

    @Test
    void queriesAreTypedAndDoNotExposeRawMaps() {
        assertNotNull(AlertQuery.of(ManagementPageRequest.of(0, 20, "createdAt")));
    }
}

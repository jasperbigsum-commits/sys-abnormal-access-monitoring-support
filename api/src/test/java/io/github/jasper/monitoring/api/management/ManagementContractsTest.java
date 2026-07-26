package io.github.jasper.monitoring.api.management;

import io.github.jasper.monitoring.api.management.command.AlertAcknowledgeCommand;
import io.github.jasper.monitoring.api.management.command.AlertCloseCommand;
import io.github.jasper.monitoring.api.management.command.AlertFalsePositiveCommand;
import io.github.jasper.monitoring.api.management.command.AlertStartInvestigationCommand;
import io.github.jasper.monitoring.api.management.command.ControlApprovalCommand;
import io.github.jasper.monitoring.api.management.command.ControlRejectionCommand;
import io.github.jasper.monitoring.api.management.command.WhitelistGrantCommand;
import io.github.jasper.monitoring.api.management.command.WhitelistRevokeCommand;
import io.github.jasper.monitoring.api.management.model.AlertView;
import io.github.jasper.monitoring.api.management.query.AlertQuery;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

class ManagementContractsTest {
    @Test
    void rejectsInvalidPageRequest() {
        assertThrows(IllegalArgumentException.class, () -> ManagementPageRequest.of(0, 201, AlertQuery.Sort.CREATED_AT));
        assertThrows(IllegalArgumentException.class, () -> ManagementPageRequest.of(-1, 20, AlertQuery.Sort.CREATED_AT));
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
    void serviceSupportAuthorizesScopedResourceBeforeAdapterAccess() {
        final ManagementActor actor = ManagementActor.of("operator", "host-a");
        final ManagementOperation[] captured = new ManagementOperation[1];
        final ManagementResource[] resource = new ManagementResource[1];
        ManagementAuthorizer authorizer = (a, operation, authorizedResource) -> {
            assertSame(actor, a);
            captured[0] = operation;
            resource[0] = authorizedResource;
        };

        assertSame(actor, ManagementServiceSupport.authorize(authorizer, actor,
            ManagementOperation.ALERT_READ, "alert", "alert-1"));
        assertEquals(ManagementOperation.ALERT_READ, captured[0]);
        assertEquals("alert", resource[0].getType());
        assertEquals("alert-1", resource[0].getId());
        assertEquals("host-a", resource[0].getSystemScope());
    }

    @Test
    void serviceSupportRejectsInvalidAdapterInputs() {
        ManagementActor actor = ManagementActor.of("operator", "host-a");
        ManagementAuthorizer authorizer = (a, operation, resource) -> { };
        assertThrows(IllegalArgumentException.class, () -> ManagementServiceSupport.authorize(
            authorizer, actor, ManagementOperation.ALERT_READ, " ", "alert-1"));
        assertThrows(IllegalArgumentException.class, () -> ManagementServiceSupport.authorize(
            authorizer, actor, ManagementOperation.ALERT_READ, "alert", " "));
        assertThrows(NullPointerException.class, () -> ManagementServiceSupport.authorize(
            null, actor, ManagementOperation.ALERT_READ, "alert", "alert-1"));
    }

    @Test
    void commandsCarryExpectedVersionAndReason() {
        AlertAcknowledgeCommand command = AlertAcknowledgeCommand.of("a", 3L, "triaged", "ack-3");
        assertEquals(3L, command.getExpectedVersion());
        assertEquals("triaged", command.getReason());
        assertThrows(IllegalArgumentException.class, () -> AlertAcknowledgeCommand.of("a", 0L, "x", "ack-0"));
    }

    @Test
    void generatedCommandKeysIncludeOperationIdentity() {
        assertNotEquals(ControlApprovalCommand.of("c", 3L, "approve").getIdempotencyKey(),
            ControlRejectionCommand.of("c", 3L, "reject").getIdempotencyKey());
        assertNotEquals(WhitelistGrantCommand.of("w", 2L, "grant").getIdempotencyKey(),
            WhitelistRevokeCommand.of("w", 2L, "revoke").getIdempotencyKey());
        assertNotEquals(AlertStartInvestigationCommand.of("a", 2L, "investigate").getIdempotencyKey(),
            AlertCloseCommand.of("a", 2L, "close").getIdempotencyKey());
        assertNotEquals(AlertCloseCommand.of("a", 2L, "close").getIdempotencyKey(),
            AlertFalsePositiveCommand.of("a", 2L, "false-positive").getIdempotencyKey());
        assertThrows(IllegalArgumentException.class,
            () -> io.github.jasper.monitoring.api.management.command.VersionedReasonCommand.of("a", 2L, "generic"));
    }

    @Test
    void generatedCommandKeyRemainsBoundedForMaximumResourceId() {
        String resourceId = repeated('r', 256);

        String key = AlertCloseCommand.of(resourceId, 2L, "close").getIdempotencyKey();

        assertTrue(key.length() <= 128);
        assertTrue(key.startsWith("alert-close:"));
        assertNotEquals(key, AlertCloseCommand.of(repeated('s', 256), 2L, "close").getIdempotencyKey());
    }

    private static String repeated(char value, int count) {
        StringBuilder result = new StringBuilder(count);
        for (int i = 0; i < count; i++) {
            result.append(value);
        }
        return result.toString();
    }

    @Test
    void queriesAreTypedAndDoNotExposeRawMaps() {
        assertNotNull(AlertQuery.of(ManagementPageRequest.of(0, 20, AlertQuery.Sort.CREATED_AT)));
    }
}

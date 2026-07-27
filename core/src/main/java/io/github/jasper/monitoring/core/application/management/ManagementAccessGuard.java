package io.github.jasper.monitoring.core.application.management;

import io.github.jasper.monitoring.api.management.ManagementActor;
import io.github.jasper.monitoring.api.management.ManagementAuthorizer;
import io.github.jasper.monitoring.api.management.ManagementOperation;
import io.github.jasper.monitoring.api.management.ManagementResource;
import io.github.jasper.monitoring.api.error.ManagementAccessDeniedException;
import io.github.jasper.monitoring.core.domain.management.ManagementAuditRecord;
import io.github.jasper.monitoring.core.port.ManagementAuditRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Enforces the trusted authorization boundary before any management persistence read. */
public final class ManagementAccessGuard {
    private final ManagementAuthorizer authorizer;
    private final ManagementAuditRepository audits;
    private final Clock clock;

    public ManagementAccessGuard(ManagementAuthorizer authorizer, ManagementAuditRepository audits, Clock clock) {
        this.authorizer = Objects.requireNonNull(authorizer, "authorizer");
        this.audits = Objects.requireNonNull(audits, "audits");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public void require(ManagementActor actor, ManagementOperation operation, String type, String id) {
        Objects.requireNonNull(actor, "actor");
        try {
            authorizer.require(actor, operation, ManagementResource.of(type, id, actor.getSystemScope()));
        } catch (RuntimeException denied) {
            audit(actor, operation, type, id, ManagementAuditRecord.Outcome.DENIED);
            throw denied;
        }
    }

    public void audit(ManagementActor actor, ManagementOperation operation, String type, String id,
                      ManagementAuditRecord.Outcome outcome) {
        audits.append(new ManagementAuditRecord(UUID.randomUUID().toString(), actor, operation, type, id, outcome,
            Instant.now(clock)));
    }

    public void reject(ManagementActor actor, ManagementOperation operation, String type, String id, String reason) {
        audit(actor, operation, type, id, ManagementAuditRecord.Outcome.DENIED);
        throw new ManagementAccessDeniedException(reason);
    }
}

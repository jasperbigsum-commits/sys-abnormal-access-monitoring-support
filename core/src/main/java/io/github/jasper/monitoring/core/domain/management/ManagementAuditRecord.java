package io.github.jasper.monitoring.core.domain.management;

import io.github.jasper.monitoring.api.management.ManagementActor;
import io.github.jasper.monitoring.api.management.ManagementOperation;
import java.time.Instant;
import java.util.Objects;

/** Sanitized, append-only evidence of one management authorization or operation. */
public final class ManagementAuditRecord {
    public enum Outcome { ALLOWED, DENIED, SUCCEEDED, FAILED }

    private final String auditId;
    private final String systemScope;
    private final String actorId;
    private final ManagementOperation operation;
    private final String targetType;
    private final String targetId;
    private final Outcome outcome;
    private final Instant occurredAt;

    public ManagementAuditRecord(String auditId, ManagementActor actor, ManagementOperation operation,
                                 String targetType, String targetId, Outcome outcome, Instant occurredAt) {
        this.auditId = require(auditId, "auditId");
        Objects.requireNonNull(actor, "actor");
        this.systemScope = actor.getSystemScope();
        this.actorId = actor.getActorId();
        this.operation = Objects.requireNonNull(operation, "operation");
        this.targetType = require(targetType, "targetType");
        this.targetId = require(targetId, "targetId");
        this.outcome = Objects.requireNonNull(outcome, "outcome");
        this.occurredAt = Objects.requireNonNull(occurredAt, "occurredAt");
    }

    public String getAuditId() { return auditId; }
    public String getSystemScope() { return systemScope; }
    public String getActorId() { return actorId; }
    public ManagementOperation getOperation() { return operation; }
    public String getTargetType() { return targetType; }
    public String getTargetId() { return targetId; }
    public Outcome getOutcome() { return outcome; }
    public Instant getOccurredAt() { return occurredAt; }

    private static String require(String value, String field) {
        if (value == null || value.trim().isEmpty()) throw new IllegalArgumentException(field + " must not be blank");
        return value;
    }
}

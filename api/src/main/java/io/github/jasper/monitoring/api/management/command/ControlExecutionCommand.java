package io.github.jasper.monitoring.api.management.command;

import io.github.jasper.monitoring.api.ControlActionType;
import java.time.Instant;
import java.util.Objects;

/** Trusted request to create and execute a new durable control. */
public final class ControlExecutionCommand {
    private final String idempotencyKey;
    private final String subject;
    private final ControlActionType action;
    private final Instant expiresAt;

    private ControlExecutionCommand(String idempotencyKey, String subject, ControlActionType action,
                                    Instant expiresAt) {
        this.idempotencyKey = text(idempotencyKey, "idempotencyKey");
        this.subject = text(subject, "subject");
        this.action = Objects.requireNonNull(action, "action");
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
        if (action == ControlActionType.RECORD) {
            throw new IllegalArgumentException("RECORD is not an executable control");
        }
    }

    public static ControlExecutionCommand of(String idempotencyKey, String subject, ControlActionType action,
                                              Instant expiresAt) {
        return new ControlExecutionCommand(idempotencyKey, subject, action, expiresAt);
    }

    public String getIdempotencyKey() { return idempotencyKey; }
    public String getSubject() { return subject; }
    public ControlActionType getAction() { return action; }
    public Instant getExpiresAt() { return expiresAt; }
    private static String text(String value, String name) {
        if (value == null || value.trim().isEmpty()) throw new IllegalArgumentException(name + " is required");
        return value.trim();
    }
}

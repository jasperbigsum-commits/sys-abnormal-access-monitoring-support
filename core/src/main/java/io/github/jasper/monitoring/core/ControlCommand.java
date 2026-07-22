package io.github.jasper.monitoring.core;

import io.github.jasper.monitoring.api.ControlActionType;
import java.time.Instant;

/**
 * Immutable instruction for a host-provided control handler.
 * The idempotency key must remain stable across retries of the same control action.
 */
public final class ControlCommand {
    private final String idempotencyKey;
    private final String alertId;
    private final String subject;
    private final ControlActionType action;
    private final Instant expiresAt;
    /**
     * @param idempotencyKey stable key used to prevent duplicate control execution
     * @param alertId source alert that requested the action
     * @param subject user, session, or IP scope selected by the matched rule
     * @param action action the host handler must apply
     * @param expiresAt time after which a temporary control should no longer apply
     */
    public ControlCommand(String idempotencyKey, String alertId, String subject, ControlActionType action, Instant expiresAt) {
        this.idempotencyKey = idempotencyKey;
        this.alertId = alertId;
        this.subject = subject;
        this.action = action;
        this.expiresAt = expiresAt;
    }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getAlertId() { return alertId; }
    public String getSubject() { return subject; }
    public ControlActionType getAction() { return action; }
    public Instant getExpiresAt() { return expiresAt; }
}

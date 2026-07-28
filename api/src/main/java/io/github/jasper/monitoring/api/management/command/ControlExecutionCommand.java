package io.github.jasper.monitoring.api.management.command;

import io.github.jasper.monitoring.api.ControlActionType;
import java.time.Instant;
import java.util.Objects;

/** 创建并执行新控制动作的可信请求命令。 */
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

    /** @return 控制执行命令对象 */
    public static ControlExecutionCommand of(String idempotencyKey, String subject, ControlActionType action,
                                              Instant expiresAt) {
        return new ControlExecutionCommand(idempotencyKey, subject, action, expiresAt);
    }

    /** @return 幂等键 */
    public String getIdempotencyKey() { return idempotencyKey; }
    /** @return 控制作用主体 */
    public String getSubject() { return subject; }
    /** @return 控制动作类型 */
    public ControlActionType getAction() { return action; }
    /** @return 控制过期时间 */
    public Instant getExpiresAt() { return expiresAt; }
    private static String text(String value, String name) {
        if (value == null || value.trim().isEmpty()) throw new IllegalArgumentException(name + " is required");
        return value.trim();
    }
}

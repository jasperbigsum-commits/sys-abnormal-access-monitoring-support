package io.github.jasper.monitoring.core.domain;


import io.github.jasper.monitoring.api.ControlActionType;
import java.time.Instant;

/**
 * 交给宿主控制处理器的不可变指令（Control Command）。
 *
 * <p>同一控制动作重试时必须保留相同的幂等键；处理器不得扩大 {@link #getSubject()} 所表达的
 * 用户、会话或 IP 作用域。</p>
 */
public final class ControlCommand {
    private final String idempotencyKey;
    private final String alertId;
    private final String subject;
    private final ControlActionType action;
    private final Instant expiresAt;
    /**
     * 创建一条宿主控制指令。
     *
     * @param idempotencyKey 用于防止重复执行的稳定幂等键
     * @param alertId 请求该动作的来源告警标识
     * @param subject 规则选定的用户、会话或 IP 作用域
     * @param action 宿主处理器应执行的控制动作
     * @param expiresAt 临时控制动作的失效时间
     */
    public ControlCommand(String idempotencyKey, String alertId, String subject, ControlActionType action, Instant expiresAt) {
        this.idempotencyKey = idempotencyKey;
        this.alertId = alertId;
        this.subject = subject;
        this.action = action;
        this.expiresAt = expiresAt;
    }
    /** @return 用于幂等去重的稳定键 */
    public String getIdempotencyKey() { return idempotencyKey; }
    /** @return 请求控制动作的告警标识 */
    public String getAlertId() { return alertId; }
    /** @return 控制动作应生效的用户、会话或 IP 范围 */
    public String getSubject() { return subject; }
    /** @return 请求宿主执行的控制动作类型 */
    public ControlActionType getAction() { return action; }
    /** @return 临时控制动作的失效时间；永久动作可由宿主按自身策略解释 */
    public Instant getExpiresAt() { return expiresAt; }
}

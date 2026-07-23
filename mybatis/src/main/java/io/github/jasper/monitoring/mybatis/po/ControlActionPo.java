package io.github.jasper.monitoring.mybatis.po;

import io.github.jasper.monitoring.api.ControlActionType;
import io.github.jasper.monitoring.api.ControlStatus;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/** Persistent representation of one {@code control_action} row. */
@Getter
@Setter
public final class ControlActionPo {
    /** 控制执行记录唯一标识。 */
    private String controlId;
    /** 用于防止重复执行的幂等键。 */
    private String idempotencyKey;
    /** 关联的告警唯一标识；未由告警触发时可为空。 */
    private String alertId;
    /** 控制动作作用的目标主体。 */
    private String subject;
    /** 要执行的控制动作类型。 */
    private ControlActionType action;
    /** 临时控制的失效时间；永久控制时可为空。 */
    private Instant expiresAt;
    /** 控制动作的执行结果状态。 */
    private ControlStatus status;
    /** 控制执行失败时记录的非敏感原因。 */
    private String failureReason;
    /** 控制动作执行完成的时间。 */
    private Instant executedAt;
}

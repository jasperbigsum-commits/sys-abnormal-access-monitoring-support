package io.github.jasper.monitoring.core.domain;


import io.github.jasper.monitoring.api.ControlActionType;
import io.github.jasper.monitoring.api.ControlStatus;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * 宿主控制处理器返回的执行结果，用于审计与后续重试判断。
 *
 * <p>失败原因仅应包含可公开的分类或摘要，不能写入令牌、会话原文、密码或下游系统敏感详情。</p>
 */
public final class ControlExecution {
    private static final String DEFAULT_FALLBACK_CONTROL_ID_PREFIX = "default-fallback:";

    private final String controlId;
    private final String idempotencyKey;
    private final ControlStatus status;
    private final String failureReason;
    private final boolean idempotentReplay;
    private ControlExecution(String controlId, String idempotencyKey, ControlStatus status, String failureReason, boolean idempotentReplay) {
        this.controlId = controlId;
        this.idempotencyKey = idempotencyKey;
        this.status = status;
        this.failureReason = failureReason;
        this.idempotentReplay = idempotentReplay;
    }
    /**
     * 创建成功的首次执行结果。
     *
     * @param key 本次控制动作的幂等键
     * @return 成功且非重放的执行结果
     */
    public static ControlExecution succeeded(String key) { return new ControlExecution(key, key, ControlStatus.SUCCEEDED, null, false); }
    /**
     * 创建已处理失败的执行结果。
     *
     * @param key 本次控制动作的幂等键
     * @param reason 不包含敏感信息的失败分类或摘要
     * @return 失败且非重放的执行结果
     */
    public static ControlExecution failed(String key, String reason) { return new ControlExecution(key, key, ControlStatus.FAILED, reason, false); }
    /**
     * 创建由宿主明确跳过的执行结果。
     *
     * @param key 本次控制动作的幂等键
     * @param reason 不包含敏感信息的跳过原因
     * @return 跳过且非重放的执行结果
     */
    public static ControlExecution skipped(String key, String reason) { return new ControlExecution(key, key, ControlStatus.SKIPPED, reason, false); }
    /**
     * 创建由框架默认触发器返回的可重试跳过结果。
     *
     * <p>该结果使用独立且可持久化识别的控制记录标识，以区分宿主主动返回的
     * {@link #skipped(String, String)}。当宿主后续接入同一动作时，控制服务可安全地重试它。</p>
     *
     * @param key 本次控制动作的幂等键
     * @param action 缺少宿主实现的控制动作
     * @return 默认回退产生的跳过结果
     */
    public static ControlExecution fallbackSkipped(String key, ControlActionType action) {
        return new ControlExecution(defaultFallbackControlId(key), key, ControlStatus.SKIPPED,
            "DEFAULT_TRIGGER_REQUIRES_HOST_HANDLER:" + action, false);
    }
    /**
     * 从持久化记录恢复一次控制执行。
     *
     * <p>仅持久化适配器应使用该工厂，以保留控制记录来源和后续重试语义。</p>
     *
     * @param controlId 持久化的控制记录标识
     * @param key 持久化的幂等键
     * @param status 持久化的执行状态
     * @param reason 持久化的失败或跳过原因
     * @return 未标记重放的恢复结果
     */
    public static ControlExecution restored(String controlId, String key, ControlStatus status, String reason) {
        return new ControlExecution(controlId, key, status, reason, false);
    }
    /** @return 标记为幂等重放、但保留原始状态和原因的等价结果 */
    public ControlExecution replay() { return new ControlExecution(controlId, idempotencyKey, status, failureReason, true); }
    /** @return 控制记录标识；标准宿主结果与幂等键相同，默认回退使用可识别的独立标识 */
    public String getControlId() { return controlId; }
    /** @return 用于识别同一次控制动作重试的幂等键 */
    public String getIdempotencyKey() { return idempotencyKey; }
    /** @return 宿主处理器报告的执行状态 */
    public ControlStatus getStatus() { return status; }
    /** @return 不包含敏感信息的失败或跳过原因；成功时通常为 {@code null} */
    public String getFailureReason() { return failureReason; }
    /** @return 是否从已有幂等记录返回，而非再次调用宿主处理器 */
    public boolean isIdempotentReplay() { return idempotentReplay; }
    /** @return 执行状态是否为 {@link ControlStatus#SUCCEEDED} */
    public boolean isSucceeded() { return status == ControlStatus.SUCCEEDED; }

    /** @return 当前结果是否来自框架默认回退触发器 */
    public boolean isDefaultFallback() {
        return status == ControlStatus.SKIPPED && controlId != null && idempotencyKey != null
            && controlId.equals(defaultFallbackControlId(idempotencyKey));
    }

    private static String defaultFallbackControlId(String key) {
        return DEFAULT_FALLBACK_CONTROL_ID_PREFIX + UUID.nameUUIDFromBytes(
            key.getBytes(StandardCharsets.UTF_8)).toString();
    }
}

package io.github.jasper.monitoring.core.domain;


import io.github.jasper.monitoring.api.ControlStatus;

/**
 * 宿主控制处理器返回的执行结果，用于审计与后续重试判断。
 *
 * <p>失败原因仅应包含可公开的分类或摘要，不能写入令牌、会话原文、密码或下游系统敏感详情。</p>
 */
public final class ControlExecution {
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
    /** @return 标记为幂等重放、但保留原始状态和原因的等价结果 */
    public ControlExecution replay() { return new ControlExecution(controlId, idempotencyKey, status, failureReason, true); }
    /** @return 控制记录标识；默认实现与幂等键相同 */
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
}

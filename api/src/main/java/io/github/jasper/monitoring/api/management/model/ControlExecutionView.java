package io.github.jasper.monitoring.api.management.model;

/** 授权手工控制请求的执行结果视图。 */
public final class ControlExecutionView {
    private final String idempotencyKey;
    private final String status;
    private final boolean idempotentReplay;

    private ControlExecutionView(String idempotencyKey, String status, boolean idempotentReplay) {
        this.idempotencyKey = ManagementModelValidation.text(idempotencyKey, "idempotencyKey");
        this.status = ManagementModelValidation.controlStatus(status);
        this.idempotentReplay = idempotentReplay;
    }

    /** @return 控制执行结果视图 */
    public static ControlExecutionView of(String idempotencyKey, String status, boolean idempotentReplay) {
        return new ControlExecutionView(idempotencyKey, status, idempotentReplay);
    }

    /** @return 幂等键 */
    public String getIdempotencyKey() { return idempotencyKey; }
    /** @return 执行状态 */
    public String getStatus() { return status; }
    /** @return 是否为幂等重放命中 */
    public boolean isIdempotentReplay() { return idempotentReplay; }
}

package io.github.jasper.monitoring.api.management.model;

/** Result of an authorized manual control request. */
public final class ControlExecutionView {
    private final String idempotencyKey;
    private final String status;
    private final boolean idempotentReplay;

    private ControlExecutionView(String idempotencyKey, String status, boolean idempotentReplay) {
        this.idempotencyKey = ManagementModelValidation.text(idempotencyKey, "idempotencyKey");
        this.status = ManagementModelValidation.controlStatus(status);
        this.idempotentReplay = idempotentReplay;
    }

    public static ControlExecutionView of(String idempotencyKey, String status, boolean idempotentReplay) {
        return new ControlExecutionView(idempotencyKey, status, idempotentReplay);
    }

    public String getIdempotencyKey() { return idempotencyKey; }
    public String getStatus() { return status; }
    public boolean isIdempotentReplay() { return idempotentReplay; }
}

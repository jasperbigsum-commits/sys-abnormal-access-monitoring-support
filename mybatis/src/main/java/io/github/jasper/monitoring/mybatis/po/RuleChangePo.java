package io.github.jasper.monitoring.mybatis.po;

/** Persisted rule-change payload used for exact replay validation in Java. */
public final class RuleChangePo {
    private String id;
    private String mode;
    private long version;
    private long threshold;
    private String actorId;
    private String approverId;
    private String reason;
    private String idempotencyKey;
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }
    public long getVersion() { return version; }
    public void setVersion(long version) { this.version = version; }
    public long getThreshold() { return threshold; }
    public void setThreshold(long threshold) { this.threshold = threshold; }
    public String getActorId() { return actorId; }
    public void setActorId(String actorId) { this.actorId = actorId; }
    public String getApproverId() { return approverId; }
    public void setApproverId(String approverId) { this.approverId = approverId; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
}

package io.github.jasper.monitoring.api.management.model;

/** 控制执行的一次尝试视图。 */
public final class ControlAttemptView {
    private final long attempt;
    private final String status;

    public ControlAttemptView(long attempt, String status) {
        if (attempt < 1 || status == null
            || (!"PENDING".equals(status) && !"SUCCEEDED".equals(status)
            && !"FAILED".equals(status) && !"SKIPPED".equals(status))) {
            throw new IllegalArgumentException("invalid attempt");
        }
        this.attempt = attempt;
        this.status = status;
    }

    /** @return 尝试序号（从 1 开始） */
    public long getAttempt() { return attempt; }
    /** @return 尝试状态 */
    public String getStatus() { return status; }
}

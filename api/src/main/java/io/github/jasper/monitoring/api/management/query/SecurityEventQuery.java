package io.github.jasper.monitoring.api.management.query;

import io.github.jasper.monitoring.api.management.ManagementPageRequest;

/** 安全事件查询条件，要求提供受限时间窗口。 */
public final class SecurityEventQuery extends ManagementQuery {
    /** 安全事件列表支持的排序字段。 */
    public enum Sort { OCCURRED_AT, RISK, ACTION, ID }

    private final java.time.Instant from;
    private final java.time.Instant to;

    private SecurityEventQuery(ManagementPageRequest p, java.time.Instant from, java.time.Instant to) {
        super(p);
        if (from == null || to == null || to.isBefore(from)
            || java.time.Duration.between(from, to).compareTo(java.time.Duration.ofDays(31)) > 0) {
            throw new IllegalArgumentException("bounded time range required");
        }
        this.from = from;
        this.to = to;
    }

    /** @return 安全事件查询对象 */
    public static SecurityEventQuery of(ManagementPageRequest p, java.time.Instant from, java.time.Instant to) {
        return new SecurityEventQuery(p, from, to);
    }

    /** @return 起始时间 */
    public java.time.Instant getFrom() { return from; }
    /** @return 结束时间 */
    public java.time.Instant getTo() { return to; }
}

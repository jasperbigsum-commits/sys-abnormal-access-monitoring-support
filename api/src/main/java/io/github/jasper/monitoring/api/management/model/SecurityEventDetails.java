package io.github.jasper.monitoring.api.management.model;

/** 安全事件详情视图，当前复用安全事件摘要字段。 */
public final class SecurityEventDetails extends SecurityEventSummary {
    public SecurityEventDetails(String id, String scope, String action, String status, long occurredAt) {
        super(id, scope, action, status, occurredAt);
    }
}

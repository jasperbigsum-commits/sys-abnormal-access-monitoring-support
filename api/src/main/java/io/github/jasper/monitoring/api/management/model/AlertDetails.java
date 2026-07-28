package io.github.jasper.monitoring.api.management.model;

/** 告警详情视图，当前复用告警摘要字段。 */
public final class AlertDetails extends AlertSummary {
    public AlertDetails(String id, String scope, String status, long version) {
        super(id, scope, status, version);
    }
}

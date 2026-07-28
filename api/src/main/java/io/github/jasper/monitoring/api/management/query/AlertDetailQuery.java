package io.github.jasper.monitoring.api.management.query;

import java.util.Objects;

/** 单条告警详情查询条件。 */
public final class AlertDetailQuery {
    private final String alertId;

    private AlertDetailQuery(String id) {
        this.alertId = require(id);
    }

    /** @return 告警详情查询对象 */
    public static AlertDetailQuery of(String id) {
        return new AlertDetailQuery(id);
    }

    /** @return 告警标识 */
    public String getAlertId() {
        return alertId;
    }

    private static String require(String s) {
        Objects.requireNonNull(s);
        if (s.trim().isEmpty()) throw new IllegalArgumentException("alertId must not be blank");
        return s;
    }
}

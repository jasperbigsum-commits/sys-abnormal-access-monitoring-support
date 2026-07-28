package io.github.jasper.monitoring.api.management.model;

/** 管理侧告警状态枚举。 */
public enum AlertStatus {
    OPEN,
    ACKNOWLEDGED,
    INVESTIGATING,
    CLOSED,
    FALSE_POSITIVE
}

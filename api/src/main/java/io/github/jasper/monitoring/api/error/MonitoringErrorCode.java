package io.github.jasper.monitoring.api.error;

/** Stable, machine-readable monitoring failure codes. */
public enum MonitoringErrorCode {
    REQUIRED_FIELD_MISSING("MON-001"),
    INVALID_FIELD_VALUE("MON-002"),
    UNSAFE_EVENT_ATTRIBUTE("MON-003"),
    ACTION_NOT_REGISTERED("MON-101"),
    CONFLICTING_ACTION_DEFINITION("MON-102"),
    RULE_REGISTRY_FROZEN("MON-103"),
    DUPLICATE_CONTROL_BINDING("MON-104"),
    INVALID_CONTROL_TRIGGER("MON-105"),
    DUPLICATE_INTERNAL_RULE_ID("MON-106"),
    ACTION_CATALOG_FROZEN("MON-107"),
    MANAGEMENT_VALIDATION_FAILED("MON-501"),
    MANAGEMENT_ACCESS_DENIED("MON-502"),
    MANAGEMENT_NOT_FOUND("MON-503"),
    MANAGEMENT_CONFLICT("MON-504"),
    MONITORING_SYSTEM_UNAVAILABLE("MON-505"),
    ALERT_NOT_FOUND("MON-201"),
    INVALID_ALERT_TRANSITION("MON-202"),
    ENFORCEMENT_HANDLER_REQUIRED("MON-301"),
    PERSISTENCE_OPERATION_FAILED("MON-401");

    private final String code;

    MonitoringErrorCode(String code) {
        this.code = code;
    }

    /** Returns the stable external code. */
    public String getCode() {
        return code;
    }
}

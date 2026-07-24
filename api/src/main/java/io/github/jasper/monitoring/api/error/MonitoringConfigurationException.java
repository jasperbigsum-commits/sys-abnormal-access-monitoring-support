package io.github.jasper.monitoring.api.error;

import java.util.Objects;

/** Indicates invalid monitoring startup configuration or host wiring. */
public final class MonitoringConfigurationException extends IllegalStateException implements MonitoringFailure {
    private final MonitoringErrorCode errorCode;

    public MonitoringConfigurationException(MonitoringErrorCode errorCode, String message) {
        this(errorCode, message, null);
    }

    public MonitoringConfigurationException(MonitoringErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = Objects.requireNonNull(errorCode, "errorCode");
    }

    @Override
    public MonitoringErrorCode getErrorCode() {
        return errorCode;
    }
}

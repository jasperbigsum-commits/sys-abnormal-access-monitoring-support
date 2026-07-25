package io.github.jasper.monitoring.api.error;

import java.util.Objects;

/** Indicates invalid or unsafe input supplied to a monitoring contract. */
public final class MonitoringValidationException extends IllegalArgumentException implements MonitoringFailure {
    private final MonitoringErrorCode errorCode;

    public MonitoringValidationException(MonitoringErrorCode errorCode, String message) {
        this(errorCode, message, null);
    }

    public MonitoringValidationException(MonitoringErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = Objects.requireNonNull(errorCode, "errorCode");
    }

    @Override
    public MonitoringErrorCode getErrorCode() {
        return errorCode;
    }
}

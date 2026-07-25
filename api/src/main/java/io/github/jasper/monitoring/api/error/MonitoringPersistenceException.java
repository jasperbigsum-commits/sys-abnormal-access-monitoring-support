package io.github.jasper.monitoring.api.error;

import java.util.Objects;

/** Indicates that monitoring persistence could not complete. */
public final class MonitoringPersistenceException extends RuntimeException implements MonitoringFailure {
    private final MonitoringErrorCode errorCode;

    public MonitoringPersistenceException(MonitoringErrorCode errorCode, String message) {
        this(errorCode, message, null);
    }

    public MonitoringPersistenceException(MonitoringErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = Objects.requireNonNull(errorCode, "errorCode");
    }

    @Override
    public MonitoringErrorCode getErrorCode() {
        return errorCode;
    }
}

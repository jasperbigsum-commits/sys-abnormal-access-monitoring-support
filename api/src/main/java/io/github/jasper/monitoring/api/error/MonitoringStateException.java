package io.github.jasper.monitoring.api.error;

import java.util.Objects;

/** Indicates an invalid monitoring lifecycle or registry state transition. */
public final class MonitoringStateException extends IllegalStateException implements MonitoringFailure {
    private final MonitoringErrorCode errorCode;

    public MonitoringStateException(MonitoringErrorCode errorCode, String message) {
        this(errorCode, message, null);
    }

    public MonitoringStateException(MonitoringErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = Objects.requireNonNull(errorCode, "errorCode");
    }

    @Override
    public MonitoringErrorCode getErrorCode() {
        return errorCode;
    }
}

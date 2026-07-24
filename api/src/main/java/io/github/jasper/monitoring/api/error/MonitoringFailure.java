package io.github.jasper.monitoring.api.error;

/** Common inspection contract for failures defined by the monitoring component. */
public interface MonitoringFailure {
    /** Returns the stable machine-readable failure code. */
    MonitoringErrorCode getErrorCode();
}

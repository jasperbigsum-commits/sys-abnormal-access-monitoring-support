package io.github.jasper.monitoring.api.error;
public final class MonitoringSystemException extends RuntimeException implements MonitoringFailure { public MonitoringSystemException(String message){super(message);} public MonitoringErrorCode getErrorCode(){return MonitoringErrorCode.MONITORING_SYSTEM_UNAVAILABLE;} }

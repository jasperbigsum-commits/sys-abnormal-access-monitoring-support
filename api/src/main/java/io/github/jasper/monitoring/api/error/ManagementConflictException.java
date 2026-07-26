package io.github.jasper.monitoring.api.error;
public final class ManagementConflictException extends RuntimeException implements MonitoringFailure { public ManagementConflictException(String message){super(message);} public MonitoringErrorCode getErrorCode(){return MonitoringErrorCode.MANAGEMENT_CONFLICT;} }

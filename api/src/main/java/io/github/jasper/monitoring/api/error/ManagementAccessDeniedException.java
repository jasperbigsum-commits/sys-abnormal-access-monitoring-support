package io.github.jasper.monitoring.api.error;
public final class ManagementAccessDeniedException extends RuntimeException implements MonitoringFailure { public ManagementAccessDeniedException(String message){super(message);} public MonitoringErrorCode getErrorCode(){return MonitoringErrorCode.MANAGEMENT_ACCESS_DENIED;} }

package io.github.jasper.monitoring.api.error;
public final class ManagementNotFoundException extends RuntimeException implements MonitoringFailure { public ManagementNotFoundException(String message){super(message);} public MonitoringErrorCode getErrorCode(){return MonitoringErrorCode.MANAGEMENT_NOT_FOUND;} }

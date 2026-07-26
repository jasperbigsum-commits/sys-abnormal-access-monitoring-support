package io.github.jasper.monitoring.api.error;
public final class ManagementValidationException extends IllegalArgumentException implements MonitoringFailure { public ManagementValidationException(String message){super(message);} public MonitoringErrorCode getErrorCode(){return MonitoringErrorCode.MANAGEMENT_VALIDATION_FAILED;} }

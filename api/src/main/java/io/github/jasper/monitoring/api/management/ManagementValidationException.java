package io.github.jasper.monitoring.api.management;
import io.github.jasper.monitoring.api.error.*;
public class ManagementValidationException extends IllegalArgumentException implements MonitoringFailure { private final MonitoringErrorCode code; public ManagementValidationException(MonitoringErrorCode code,String message){super(message);this.code=code;} public MonitoringErrorCode getErrorCode(){return code;} }

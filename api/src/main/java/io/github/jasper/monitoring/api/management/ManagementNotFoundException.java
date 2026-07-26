package io.github.jasper.monitoring.api.management;
import io.github.jasper.monitoring.api.error.*;
public class ManagementNotFoundException extends RuntimeException implements MonitoringFailure { private final MonitoringErrorCode code; public ManagementNotFoundException(MonitoringErrorCode code,String message){super(message);this.code=code;} public MonitoringErrorCode getErrorCode(){return code;} }

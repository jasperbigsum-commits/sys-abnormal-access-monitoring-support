package io.github.jasper.monitoring.api.management;
import io.github.jasper.monitoring.api.error.*;
/** Indicates that the management use case could not be completed by the system. */
public final class MonitoringSystemException extends RuntimeException implements MonitoringFailure { private final MonitoringErrorCode code; public MonitoringSystemException(MonitoringErrorCode code,String message){super(message);this.code=code;} public MonitoringErrorCode getErrorCode(){return code;} }

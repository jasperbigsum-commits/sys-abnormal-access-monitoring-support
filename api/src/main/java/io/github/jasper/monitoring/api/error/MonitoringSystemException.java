package io.github.jasper.monitoring.api.error;
/** 监测系统依赖不可用，导致当前操作无法可靠完成。 */
public final class MonitoringSystemException extends RuntimeException implements MonitoringFailure { public MonitoringSystemException(String message){super(message);} public MonitoringErrorCode getErrorCode(){return MonitoringErrorCode.MONITORING_SYSTEM_UNAVAILABLE;} }

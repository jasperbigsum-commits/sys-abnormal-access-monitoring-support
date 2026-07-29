package io.github.jasper.monitoring.api.error;
/** 管理操作引用的资源不存在，或对当前调用方不可见。 */
public final class ManagementNotFoundException extends RuntimeException implements MonitoringFailure { public ManagementNotFoundException(String message){super(message);} public MonitoringErrorCode getErrorCode(){return MonitoringErrorCode.MANAGEMENT_NOT_FOUND;} }

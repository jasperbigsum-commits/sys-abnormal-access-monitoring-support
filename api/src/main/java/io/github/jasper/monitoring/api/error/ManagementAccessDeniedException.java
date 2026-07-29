package io.github.jasper.monitoring.api.error;
/** 管理操作未通过宿主提供的授权边界。 */
public final class ManagementAccessDeniedException extends RuntimeException implements MonitoringFailure { public ManagementAccessDeniedException(String message){super(message);} public MonitoringErrorCode getErrorCode(){return MonitoringErrorCode.MANAGEMENT_ACCESS_DENIED;} }

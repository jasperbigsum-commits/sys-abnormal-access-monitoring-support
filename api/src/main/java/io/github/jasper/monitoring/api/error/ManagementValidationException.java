package io.github.jasper.monitoring.api.error;
/** 管理命令违反输入、状态或安全策略校验。 */
public final class ManagementValidationException extends IllegalArgumentException implements MonitoringFailure { public ManagementValidationException(String message){super(message);} public MonitoringErrorCode getErrorCode(){return MonitoringErrorCode.MANAGEMENT_VALIDATION_FAILED;} }

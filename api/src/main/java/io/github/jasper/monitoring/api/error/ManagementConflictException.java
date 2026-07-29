package io.github.jasper.monitoring.api.error;
/** 管理操作因乐观锁版本或其他并发条件不再匹配而被拒绝。 */
public final class ManagementConflictException extends RuntimeException implements MonitoringFailure { public ManagementConflictException(String message){super(message);} public MonitoringErrorCode getErrorCode(){return MonitoringErrorCode.MANAGEMENT_CONFLICT;} }

package io.github.jasper.monitoring.api.management;

import io.github.jasper.monitoring.api.error.MonitoringErrorCode;
import io.github.jasper.monitoring.api.error.MonitoringFailure;

/** 管理子系统运行异常，携带标准化错误码。 */
public class ManagementSystemException extends RuntimeException implements MonitoringFailure {
    private final MonitoringErrorCode code;

    /** 创建一个带错误码的管理系统异常。 */
    public ManagementSystemException(MonitoringErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    /** @return 标准化错误码 */
    public MonitoringErrorCode getErrorCode() {
        return code;
    }
}

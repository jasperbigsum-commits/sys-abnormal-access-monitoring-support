package io.github.jasper.monitoring.api;

/** 宿主系统尝试执行控制动作后上报的结果。 */
public enum ControlStatus {
    /** 控制动作已成功执行。 */
    SUCCEEDED,
    /** 控制动作执行失败；失败信息必须保留审计记录。 */
    FAILED,
    /** 控制动作被有意跳过，例如因幂等去重。 */
    SKIPPED
}

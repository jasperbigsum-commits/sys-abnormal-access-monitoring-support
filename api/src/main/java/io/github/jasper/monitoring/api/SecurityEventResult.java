package io.github.jasper.monitoring.api;

/** 安全事件所表示操作的最终服务端结果。 */
public enum SecurityEventResult {
    /** 操作已成功完成。 */
    SUCCESS,
    /** 操作在完成前失败。 */
    FAILURE,
    /** 操作被授权决策或安全控制拒绝。 */
    DENIED
}

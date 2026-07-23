package io.github.jasper.monitoring.api;

/** 监测与控制请求的运行策略。 */
public enum MonitoringMode {
    /** 收集证据、评估规则并产生告警，但不执行控制动作。 */
    OBSERVE,
    /** 通过已注册的宿主控制处理器执行请求的控制动作。 */
    ENFORCE
}

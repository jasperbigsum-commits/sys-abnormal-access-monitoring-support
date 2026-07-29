package io.github.jasper.monitoring.api.action;

/**
 * 监测链路失败时对业务调用的处置策略。
 * <p>
 * 该策略不是“规则命中后的控制动作”，而是“监测系统自身不可用/数据不完整时”的降级或阻断策略。
 */
public enum ActionFailurePolicy {
    /**
     * 仅观测，不影响业务执行。
     * 场景：低风险查询、历史审计优先场景；即使监测失败也先保证业务可用。
     */
    OBSERVE_ONLY,
    /**
     * 失败即阻断（Fail Closed）。
     * 场景：高风险导出、权限提升；关键监测前置条件不满足时直接拒绝业务调用。
     */
    FAIL_CLOSED;

    /** @return 当前策略是否不弱于给定策略（FAIL_CLOSED 比 OBSERVE_ONLY 更严格）。 */
    public boolean isAtLeast(ActionFailurePolicy other) {
        return ordinal() >= other.ordinal();
    }

    static ActionFailurePolicy strictest(ActionFailurePolicy first, ActionFailurePolicy second) {
        return first.isAtLeast(second) ? first : second;
    }
}

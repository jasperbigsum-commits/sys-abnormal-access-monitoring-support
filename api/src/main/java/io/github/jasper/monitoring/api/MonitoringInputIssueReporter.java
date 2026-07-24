package io.github.jasper.monitoring.api;

/**
 * 报告监测输入质量问题的可选框架无关端口。
 */
@FunctionalInterface
public interface MonitoringInputIssueReporter {
    /**
     * 报告一个事件草稿的稳定输入质量结论。
     *
     * <p>实现不得输出原始参数值、异常消息、凭据或请求载荷。</p>
     *
     * @param draft 已清洗的事件草稿
     * @param validation 包含稳定问题码的输入质量结论
     */
    void report(SecurityEventDraft draft, EventInputValidation validation);

    /**
     * @return 忽略所有输入质量问题的无操作报告器
     */
    static MonitoringInputIssueReporter noop() {
        return (draft, validation) -> { };
    }
}

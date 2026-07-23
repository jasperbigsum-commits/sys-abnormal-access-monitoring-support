package io.github.jasper.monitoring.core.domain;




import java.time.Instant;

/** 将请求的控制指令与实际执行结果配对的不可变审计记录。 */
public final class ControlRecord {
    private final ControlCommand command;
    private final ControlExecution execution;
    private final Instant executedAt;
    /**
     * @param command 请求执行的控制指令
     * @param execution 被选中宿主处理器返回的结果
     * @param executedAt 服务端记录的执行时间
     */
    public ControlRecord(ControlCommand command, ControlExecution execution, Instant executedAt) {
        this.command = command;
        this.execution = execution;
        this.executedAt = executedAt;
    }
    /** @return 被请求执行的控制指令 */
    public ControlCommand getCommand() { return command; }
    /** @return 控制处理器的执行结果 */
    public ControlExecution getExecution() { return execution; }
    /** @return 服务端记录控制结果的时间 */
    public Instant getExecutedAt() { return executedAt; }
}

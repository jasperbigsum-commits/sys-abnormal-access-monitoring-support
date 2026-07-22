package io.github.jasper.monitoring.core;

import java.time.Instant;

/** Immutable audit record that pairs a requested control with its execution outcome. */
public final class ControlRecord {
    private final ControlCommand command;
    private final ControlExecution execution;
    private final Instant executedAt;
    /**
     * @param command requested control action
     * @param execution result returned by the selected host handler
     * @param executedAt server-side execution timestamp
     */
    public ControlRecord(ControlCommand command, ControlExecution execution, Instant executedAt) {
        this.command = command;
        this.execution = execution;
        this.executedAt = executedAt;
    }
    public ControlCommand getCommand() { return command; }
    public ControlExecution getExecution() { return execution; }
    public Instant getExecutedAt() { return executedAt; }
}

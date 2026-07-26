package io.github.jasper.monitoring.core.domain.control;

import io.github.jasper.monitoring.api.control.ControlStatus;
import io.github.jasper.monitoring.core.domain.ControlExecution;

/** Versioned durable control state used by the execution use case. */
public final class StoredControl {
    private final ControlExecution execution;
    private final long version;
    public StoredControl(ControlExecution execution, long version) { this.execution = execution; this.version = version; }
    public ControlExecution execution() { return execution; }
    public ControlStatus status() { return execution.getStatus(); }
    public long version() { return version; }
}

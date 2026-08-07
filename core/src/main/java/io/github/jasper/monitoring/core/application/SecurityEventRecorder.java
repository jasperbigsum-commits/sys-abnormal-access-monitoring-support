package io.github.jasper.monitoring.core.application;

import io.github.jasper.monitoring.api.event.ActionExecution;

/** Persists one security event without running monitoring rules. */
@FunctionalInterface
public interface SecurityEventRecorder {
    void record(ActionExecution execution);
}

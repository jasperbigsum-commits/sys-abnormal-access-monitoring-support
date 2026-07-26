package io.github.jasper.monitoring.api.event;

/**
 * Read-only context for one monitored action execution.
 *
 * <p>The API intentionally exposes no mutators. Concrete execution data and
 * outcome ownership are introduced by the runtime assembly pipeline.</p>
 */
public interface ActionExecution {
}

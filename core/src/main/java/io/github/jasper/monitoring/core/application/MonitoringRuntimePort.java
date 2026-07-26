package io.github.jasper.monitoring.core.application;

import io.github.jasper.monitoring.api.action.ActionDefinition;
import io.github.jasper.monitoring.api.action.ActionType;
import io.github.jasper.monitoring.api.event.ActionExecution;
import io.github.jasper.monitoring.api.fact.ActionFacts;

/** Runtime-owned action resolution and fact collection boundary. */
public interface MonitoringRuntimePort {
    ActionDefinition resolve(Class<? extends ActionType> actionType);
    ActionFacts collect(ActionExecution execution, ActionDefinition action);
}

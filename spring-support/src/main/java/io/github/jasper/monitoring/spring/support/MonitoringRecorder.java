package io.github.jasper.monitoring.spring.support;

import io.github.jasper.monitoring.api.MonitoringContextAccessor;
import io.github.jasper.monitoring.api.action.ActionType;
import io.github.jasper.monitoring.api.event.ActionExecution;
import io.github.jasper.monitoring.api.event.ActionOutcome;
import io.github.jasper.monitoring.api.fact.ActionFacts;
import io.github.jasper.monitoring.api.fact.FactSource;
import io.github.jasper.monitoring.core.application.MonitoringService;
import io.github.jasper.monitoring.core.application.SecurityEventAssembler;
import java.util.Objects;

/** Concise explicit monitoring entry point for the current trusted request context. */
public final class MonitoringRecorder {
    private final MonitoringService monitoring;
    private final MonitoringContextAccessor context;

    public MonitoringRecorder(MonitoringService monitoring, MonitoringContextAccessor context) {
        this.monitoring = Objects.requireNonNull(monitoring, "monitoring");
        this.context = Objects.requireNonNull(context, "context");
    }

    /** Records server-derived facts without manually assembling an {@link ActionExecution}. */
    public SecurityEventAssembler.AssemblyResult record(Class<? extends ActionType> actionType,
            ActionOutcome outcome, ActionFacts facts) {
        return monitoring.monitor(ActionExecution.of(Objects.requireNonNull(actionType, "actionType"),
            context.requestContext(), context.identityContext(), Objects.requireNonNull(outcome, "outcome"),
            Objects.requireNonNull(facts, "facts"), FactSource.HOST_PROVIDER));
    }
}

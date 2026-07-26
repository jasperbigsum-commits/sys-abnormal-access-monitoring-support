package io.github.jasper.monitoring.api.event;

import io.github.jasper.monitoring.api.IdentityContext;
import io.github.jasper.monitoring.api.MonitoringRequestContext;
import io.github.jasper.monitoring.api.SecurityEventResult;
import io.github.jasper.monitoring.api.action.ActionType;
import io.github.jasper.monitoring.api.fact.ActionFacts;
import io.github.jasper.monitoring.api.fact.FactSource;

/**
 * Read-only context for one monitored action execution.
 *
 * <p>The API intentionally exposes no mutators. Concrete execution data and
 * outcome ownership are introduced by the runtime assembly pipeline.</p>
 */
public interface ActionExecution {

    Class<? extends ActionType> getActionType();
    MonitoringRequestContext getRequestContext();
    IdentityContext getIdentityContext();
    ActionOutcome getOutcome();
    default ActionFacts getSuppliedFacts() { return ActionFacts.builder().build(); }
    default FactSource getSuppliedFactSource() { return FactSource.HOST_PROVIDER; }

    static ActionExecution of(Class<? extends ActionType> actionType, MonitoringRequestContext request, IdentityContext identity,
                              ActionOutcome outcome) {
        return new ImmutableActionExecution(actionType, request, identity, outcome,
            ActionFacts.builder().build(), FactSource.HOST_PROVIDER);
    }

    static ActionExecution of(Class<? extends ActionType> actionType, MonitoringRequestContext request,
                              IdentityContext identity, ActionOutcome outcome, ActionFacts facts,
                              FactSource factSource) {
        return new ImmutableActionExecution(actionType, request, identity, outcome, facts, factSource);
    }

    final class ImmutableActionExecution implements ActionExecution {
        private final MonitoringRequestContext request;
        private final IdentityContext identity;
        private final ActionOutcome outcome;
        private final Class<? extends ActionType> actionType;
        private final ActionFacts suppliedFacts;
        private final FactSource suppliedFactSource;

        private ImmutableActionExecution(Class<? extends ActionType> actionType, MonitoringRequestContext request, IdentityContext identity,
                                         ActionOutcome outcome, ActionFacts suppliedFacts,
                                         FactSource suppliedFactSource) {
            this.actionType = java.util.Objects.requireNonNull(actionType, "actionType");
            this.request = java.util.Objects.requireNonNull(request, "request");
            this.identity = java.util.Objects.requireNonNull(identity, "identity");
            this.outcome = java.util.Objects.requireNonNull(outcome, "outcome");
            this.suppliedFacts = java.util.Objects.requireNonNull(suppliedFacts, "suppliedFacts");
            this.suppliedFactSource = java.util.Objects.requireNonNull(suppliedFactSource, "suppliedFactSource");
        }

        @Override public Class<? extends ActionType> getActionType() { return actionType; }
        @Override public MonitoringRequestContext getRequestContext() { return request; }
        @Override public IdentityContext getIdentityContext() { return identity; }
        @Override public ActionOutcome getOutcome() { return outcome; }
        @Override public ActionFacts getSuppliedFacts() { return suppliedFacts; }
        @Override public FactSource getSuppliedFactSource() { return suppliedFactSource; }
    }
}

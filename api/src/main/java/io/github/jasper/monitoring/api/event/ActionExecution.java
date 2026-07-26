package io.github.jasper.monitoring.api.event;

import io.github.jasper.monitoring.api.IdentityContext;
import io.github.jasper.monitoring.api.MonitoringRequestContext;
import io.github.jasper.monitoring.api.SecurityEventResult;

/**
 * Read-only context for one monitored action execution.
 *
 * <p>The API intentionally exposes no mutators. Concrete execution data and
 * outcome ownership are introduced by the runtime assembly pipeline.</p>
 */
public interface ActionExecution {

    default MonitoringRequestContext getRequestContext() { return null; }

    default IdentityContext getIdentityContext() { return null; }

    default ActionOutcome getOutcome() { return null; }

    static ActionExecution of(MonitoringRequestContext request, IdentityContext identity,
                              ActionOutcome outcome) {
        return new ImmutableActionExecution(request, identity, outcome);
    }

    final class ImmutableActionExecution implements ActionExecution {
        private final MonitoringRequestContext request;
        private final IdentityContext identity;
        private final ActionOutcome outcome;

        private ImmutableActionExecution(MonitoringRequestContext request, IdentityContext identity,
                                         ActionOutcome outcome) {
            this.request = java.util.Objects.requireNonNull(request, "request");
            this.identity = java.util.Objects.requireNonNull(identity, "identity");
            this.outcome = java.util.Objects.requireNonNull(outcome, "outcome");
        }

        @Override public MonitoringRequestContext getRequestContext() { return request; }
        @Override public IdentityContext getIdentityContext() { return identity; }
        @Override public ActionOutcome getOutcome() { return outcome; }
    }
}

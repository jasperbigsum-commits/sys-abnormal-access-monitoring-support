package io.github.jasper.monitoring.core.application;

import io.github.jasper.monitoring.api.AccountType;
import io.github.jasper.monitoring.api.EventFactSource;
import io.github.jasper.monitoring.api.EventInputIssue;
import io.github.jasper.monitoring.api.EventInputValidation;
import io.github.jasper.monitoring.api.IdentityContext;
import io.github.jasper.monitoring.api.MonitoringRequestContext;
import io.github.jasper.monitoring.api.SecurityEventDraft;
import io.github.jasper.monitoring.api.action.ActionDefinition;
import io.github.jasper.monitoring.api.event.ActionExecution;
import io.github.jasper.monitoring.api.event.ActionOutcome;
import io.github.jasper.monitoring.api.event.ObservationIssue;
import io.github.jasper.monitoring.api.fact.ActionFacts;
import io.github.jasper.monitoring.api.fact.FactType;
import io.github.jasper.monitoring.core.domain.SecurityEvent;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Single owner of conversion from trusted execution inputs to domain events. */
public final class SecurityEventAssembler {
    private final String systemId;
    private final Clock clock;

    public SecurityEventAssembler(String systemId, Clock clock) {
        if (systemId == null || systemId.trim().isEmpty()) throw new IllegalArgumentException("systemId is required");
        this.systemId = systemId;
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public AssemblyResult assemble(ActionDefinition action, ActionExecution execution, ActionFacts facts) {
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(execution, "execution");
        Objects.requireNonNull(facts, "facts");
        MonitoringRequestContext request = Objects.requireNonNull(execution.getRequestContext(), "requestContext");
        IdentityContext identity = Objects.requireNonNull(execution.getIdentityContext(), "identityContext");
        ActionOutcome outcome = Objects.requireNonNull(execution.getOutcome(), "outcome");
        SecurityEventDraft.Builder draft = SecurityEventDraft.builder()
            .eventType(action.getEventType()).action(action.getCode()).result(outcome.getResult())
            .sourceIp(request.getSourceIp()).requestId(request.getRequestId()).traceId(request.getTraceId())
            .userId(identity.getUserId()).accountType(identity.getAccountType()).roleIds(identity.getRoleIds())
            .sessionIdHash(identity.getSessionIdHash()).resourceType(action.getResourceType())
            .occurredAt(Instant.now(clock)).reasonCode(outcome.getReasonCode());
        List<ObservationIssue> observations = new ArrayList<ObservationIssue>();
        List<EventInputIssue> inputIssues = new ArrayList<EventInputIssue>();
        for (Class<? extends FactType<?>> fact : action.getRequiredFacts()) {
            if (!facts.asMap().containsKey(fact)) {
                String name = fact.getSimpleName();
                observations.add(new ObservationIssue("MISSING_FACT", name));
                inputIssues.add(EventInputIssue.missing("action-" + action.getCode().replace(':', '-'),
                    name, EventFactSource.SERVER_COMPUTED));
            }
        }
        EventInputValidation validation = inputIssues.isEmpty() ? EventInputValidation.valid()
            : EventInputValidation.incomplete(inputIssues,
                Collections.singleton("action-" + action.getCode().replace(':', '-')));
        SecurityEvent event = SecurityEvent.from(draft.build(), systemId, UUID.randomUUID().toString(),
            Instant.now(clock), validation);
        return new AssemblyResult(event, observations);
    }

    public static final class AssemblyResult {
        private final SecurityEvent event;
        private final List<ObservationIssue> issues;
        private AssemblyResult(SecurityEvent event, List<ObservationIssue> issues) {
            this.event = event;
            this.issues = Collections.unmodifiableList(new ArrayList<ObservationIssue>(issues));
        }
        public SecurityEvent getEvent() { return event; }
        public List<ObservationIssue> getIssues() { return issues; }
    }
}

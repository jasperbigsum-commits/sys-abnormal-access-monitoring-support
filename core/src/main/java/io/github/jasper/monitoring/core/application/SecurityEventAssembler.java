package io.github.jasper.monitoring.core.application;

import io.github.jasper.monitoring.api.AccountType;
import io.github.jasper.monitoring.api.EventFactSource;
import io.github.jasper.monitoring.api.EventInputIssue;
import io.github.jasper.monitoring.api.EventInputValidation;
import io.github.jasper.monitoring.api.IdentityContext;
import io.github.jasper.monitoring.api.MonitoringRequestContext;
import io.github.jasper.monitoring.api.SecurityEventDraft;
import io.github.jasper.monitoring.api.action.ActionDefinition;
import io.github.jasper.monitoring.api.action.ActionType;
import io.github.jasper.monitoring.api.event.ActionExecution;
import io.github.jasper.monitoring.api.event.ActionOutcome;
import io.github.jasper.monitoring.api.event.ObservationIssue;
import io.github.jasper.monitoring.api.fact.ActionFacts;
import io.github.jasper.monitoring.api.fact.FactType;
import io.github.jasper.monitoring.api.rule.RuleType;
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

    public AssemblyResult assemble(Class<? extends ActionType> resolvedType, ActionDefinition action,
                                   ActionExecution execution, ActionFacts facts) {
        if (!Objects.equals(Objects.requireNonNull(resolvedType, "resolvedType"),
            Objects.requireNonNull(execution, "execution").getActionType())) {
            throw new IllegalArgumentException("Resolved action type does not match execution action type");
        }
        Objects.requireNonNull(action, "action");
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
        java.util.Set<Class<? extends RuleType>> ineligible = new java.util.LinkedHashSet<Class<? extends RuleType>>();
        if (!inputIssues.isEmpty()) ineligible.addAll(action.getRuleTypes());
        return new AssemblyResult(event, facts, observations, ineligible);
    }

    public static final class AssemblyResult {
        private final SecurityEvent event;
        private final ActionFacts facts;
        private final List<ObservationIssue> issues;
        private final java.util.Set<Class<? extends RuleType>> ineligibleRuleTypes;
        private AssemblyResult(SecurityEvent event, ActionFacts facts, List<ObservationIssue> issues,
                               java.util.Set<Class<? extends RuleType>> ineligibleRuleTypes) {
            this.event = event;
            this.facts = facts;
            this.issues = Collections.unmodifiableList(new ArrayList<ObservationIssue>(issues));
            this.ineligibleRuleTypes = Collections.unmodifiableSet(
                new java.util.LinkedHashSet<Class<? extends RuleType>>(ineligibleRuleTypes));
        }
        public SecurityEvent getEvent() { return event; }
        public ActionFacts getFacts() { return facts; }
        public List<ObservationIssue> getIssues() { return issues; }
        public java.util.Set<Class<? extends RuleType>> getIneligibleRuleTypes() { return ineligibleRuleTypes; }
    }
}

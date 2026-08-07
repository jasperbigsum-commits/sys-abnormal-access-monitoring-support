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
import io.github.jasper.monitoring.api.fact.BuiltInFacts;
import io.github.jasper.monitoring.api.fact.FactDefinition;
import io.github.jasper.monitoring.api.fact.FactType;
import io.github.jasper.monitoring.api.rule.RuleType;
import io.github.jasper.monitoring.core.domain.SecurityEvent;
import io.github.jasper.monitoring.core.domain.EventFact;
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
        return assemble(resolvedType, action, execution, facts, Collections.<EventFact>emptyList());
    }

    public AssemblyResult assemble(Class<? extends ActionType> resolvedType, ActionDefinition action,
                                   ActionExecution execution, ActionFacts facts,
                                   List<EventFact> persistedFacts) {
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
            .eventType(action.resolveEventType(outcome.getResult())).action(action.getCode()).result(outcome.getResult())
            .sourceIp(request.getSourceIp()).requestId(request.getRequestId()).traceId(request.getTraceId())
            .userId(identity.getUserId()).accountType(identity.getAccountType()).roleIds(identity.getRoleIds())
            .sessionIdHash(identity.getSessionIdHash()).resourceType(action.getResourceType())
            .occurredAt(Instant.now(clock)).reasonCode(outcome.getReason() == null ? null : outcome.getReason().getCode())
            .latencyMs(outcome.getLatencyMs());
        String resourceId = facts.get(BuiltInFacts.ResourceId.class);
        String orgScope = facts.get(BuiltInFacts.OrgScope.class);
        String attemptedAccountHash = facts.get(BuiltInFacts.LoginSubjectKey.class);
        Long dataCount = facts.get(BuiltInFacts.DataCount.class);
        BuiltInFacts.SensitivityLevel sensitivity = facts.get(BuiltInFacts.Sensitivity.class);
        if (resourceId != null) draft.resourceId(resourceId);
        if (orgScope != null) draft.orgScope(orgScope);
        if (attemptedAccountHash != null) draft.attemptedAccountHash(attemptedAccountHash);
        if (dataCount != null) draft.dataCount(dataCount.longValue());
        putAttribute(draft, BuiltInFacts.SENSITIVITY, sensitivity);
        putAttribute(draft, BuiltInFacts.DIFFERENT_NETWORKS, facts.get(BuiltInFacts.DifferentNetworks.class));
        putAttribute(draft, BuiltInFacts.SEQUENTIAL_ACCESS, facts.get(BuiltInFacts.SequentialAccess.class));
        putAttribute(draft, BuiltInFacts.SENSITIVE, facts.get(BuiltInFacts.Sensitive.class));
        putAttribute(draft, BuiltInFacts.WORK_HOURS, facts.get(BuiltInFacts.WorkHours.class));
        putAttribute(draft, BuiltInFacts.PRIVILEGE_INCREASE, facts.get(BuiltInFacts.PrivilegeIncrease.class));
        putAttribute(draft, BuiltInFacts.HIGH_PRIVILEGE, facts.get(BuiltInFacts.HighPrivilege.class));
        putAttribute(draft, BuiltInFacts.TARGET_USER_ID, facts.get(BuiltInFacts.TargetUserId.class));
        putAttribute(draft, BuiltInFacts.BASELINE_RATIO, facts.get(BuiltInFacts.BaselineRatio.class));
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
        SecurityEvent base = SecurityEvent.from(draft.build(), systemId, UUID.randomUUID().toString(),
            Instant.now(clock), validation);
        SecurityEvent event = copyWithFacts(base, persistedFacts);
        java.util.Set<Class<? extends RuleType>> ineligible = new java.util.LinkedHashSet<Class<? extends RuleType>>();
        if (!inputIssues.isEmpty()) ineligible.addAll(action.getRuleTypes());
        return new AssemblyResult(event, facts, outcome, observations, ineligible);
    }

    private static <T> void putAttribute(SecurityEventDraft.Builder draft, FactDefinition<T> definition, T value) {
        if (value != null) draft.attribute(definition.getKey(), definition.encode(value));
    }

    private static SecurityEvent copyWithFacts(SecurityEvent event, List<EventFact> facts) {
        return SecurityEvent.builder().eventId(event.getEventId()).systemId(event.getSystemId())
            .eventType(event.getEventType()).occurredAt(event.getOccurredAt()).receivedAt(event.getReceivedAt())
            .userId(event.getUserId()).accountType(event.getAccountType()).roleIds(event.getRoleIds())
            .sourceIp(event.getSourceIp()).deviceIdHash(event.getDeviceIdHash())
            .sessionIdHash(event.getSessionIdHash()).attemptedAccountHash(event.getAttemptedAccountHash())
            .requestId(event.getRequestId()).traceId(event.getTraceId())
            .action(event.getAction()).result(event.getResult()).reasonCode(event.getReasonCode())
            .resourceType(event.getResourceType()).resourceId(event.getResourceId()).orgScope(event.getOrgScope())
            .dataCount(event.getDataCount()).dataCountKnown(event.hasDataCount())
            .latencyMs(event.getLatencyMs()).latencyMsKnown(event.hasLatencyMs())
            .inputStatus(event.getInputStatus()).inputIssues(event.getInputIssues())
            .attributes(event.getAttributes()).facts(facts).build();
    }

    public static final class AssemblyResult {
        private final SecurityEvent event;
        private final ActionFacts facts;
        private final ActionOutcome outcome;
        private final List<ObservationIssue> issues;
        private final java.util.Set<Class<? extends RuleType>> ineligibleRuleTypes;
        private AssemblyResult(SecurityEvent event, ActionFacts facts, ActionOutcome outcome, List<ObservationIssue> issues,
                               java.util.Set<Class<? extends RuleType>> ineligibleRuleTypes) {
            this.event = event;
            this.facts = facts;
            this.outcome = outcome;
            this.issues = Collections.unmodifiableList(new ArrayList<ObservationIssue>(issues));
            this.ineligibleRuleTypes = Collections.unmodifiableSet(
                new java.util.LinkedHashSet<Class<? extends RuleType>>(ineligibleRuleTypes));
        }
        public SecurityEvent getEvent() { return event; }
        public ActionFacts getFacts() { return facts; }
        public ActionOutcome getOutcome() { return outcome; }
        public List<ObservationIssue> getIssues() { return issues; }
        public java.util.Set<Class<? extends RuleType>> getIneligibleRuleTypes() { return ineligibleRuleTypes; }
    }
}

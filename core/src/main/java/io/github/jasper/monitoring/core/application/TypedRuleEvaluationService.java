package io.github.jasper.monitoring.core.application;

import io.github.jasper.monitoring.api.ControlActionType;
import io.github.jasper.monitoring.api.MonitoringMode;
import io.github.jasper.monitoring.api.action.ActionDefinition;
import io.github.jasper.monitoring.api.action.ActionType;
import io.github.jasper.monitoring.api.event.ObservationIssue;
import io.github.jasper.monitoring.api.fact.ActionFacts;
import io.github.jasper.monitoring.api.fact.FactSource;
import io.github.jasper.monitoring.api.fact.FactType;
import io.github.jasper.monitoring.api.rule.RuleMode;
import io.github.jasper.monitoring.api.rule.RuleType;
import io.github.jasper.monitoring.core.application.control.ControlExecutionService;
import io.github.jasper.monitoring.core.domain.ControlCommand;
import io.github.jasper.monitoring.core.domain.RuleMatch;
import io.github.jasper.monitoring.core.domain.SecurityAlert;
import io.github.jasper.monitoring.core.domain.SecurityEvent;
import io.github.jasper.monitoring.core.domain.rule.DetectionRule;
import io.github.jasper.monitoring.core.domain.rule.RuleObservation;
import io.github.jasper.monitoring.core.domain.rule.RuleEvaluationContext;
import io.github.jasper.monitoring.core.port.AlertRepository;
import io.github.jasper.monitoring.core.port.EventRepository;
import io.github.jasper.monitoring.core.port.MonitoringTransaction;
import io.github.jasper.monitoring.core.port.NotificationChannel;
import io.github.jasper.monitoring.core.port.RuleObservationRepository;
import io.github.jasper.monitoring.core.port.WhitelistRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Typed rule, alert and durable-control orchestration for the production runtime. */
public final class TypedRuleEvaluationService implements MonitoringService.RuleEvaluationPort {
    private final EventRepository events;
    private final AlertRepository alerts;
    private final WhitelistRepository whitelist;
    private final MonitoringTransaction transaction;
    private final RuleObservationRepository observations;
    private final List<DetectionRule<? extends RuleType>> rules;
    private final MonitoringMode mode;
    private final ControlExecutionService controls;
    private final NotificationChannel notifications;
    private final Clock clock;

    public TypedRuleEvaluationService(EventRepository events, AlertRepository alerts,
            WhitelistRepository whitelist, MonitoringTransaction transaction,
            RuleObservationRepository observations,
            List<DetectionRule<? extends RuleType>> rules, MonitoringMode mode,
            ControlExecutionService controls, NotificationChannel notifications, Clock clock) {
        this.events = Objects.requireNonNull(events, "events");
        this.alerts = Objects.requireNonNull(alerts, "alerts");
        this.whitelist = Objects.requireNonNull(whitelist, "whitelist");
        this.transaction = Objects.requireNonNull(transaction, "transaction");
        this.observations = Objects.requireNonNull(observations, "observations");
        this.rules = Collections.unmodifiableList(new ArrayList<DetectionRule<? extends RuleType>>(rules));
        this.mode = Objects.requireNonNull(mode, "mode");
        this.controls = Objects.requireNonNull(controls, "controls");
        this.notifications = Objects.requireNonNull(notifications, "notifications");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public void evaluate(Class<? extends ActionType> actionType, ActionDefinition action,
            SecurityEvent event, ActionFacts facts,
            Map<Class<? extends FactType<?>>, FactSource> factSources,
            Set<Class<? extends RuleType>> ineligibleRuleTypes,
            List<ObservationIssue> issues) {
        EvaluationResult result = transaction.required(() -> evaluateInTransaction(
            actionType, action, event, facts, factSources, ineligibleRuleTypes));
        for (SecurityAlert alert : result.alerts) {
            try {
                notifications.notify(alert);
            } catch (RuntimeException ignored) {
                // Persistence is authoritative; notification remains best effort.
            }
        }
        if (mode == MonitoringMode.ENFORCE) {
            executeControls(result);
        }
    }

    private EvaluationResult evaluateInTransaction(Class<? extends ActionType> actionType,
            ActionDefinition action, SecurityEvent event, ActionFacts facts,
            Map<Class<? extends FactType<?>>, FactSource> factSources,
            Set<Class<? extends RuleType>> ineligibleRuleTypes) {
        List<SecurityEvent> history = canonicalHistory(event, events.findSince(event.getSystemId(),
            event.getOccurredAt().minus(Duration.ofDays(1))));
        RuleEvaluationContext.Builder context = RuleEvaluationContext.builder(event, actionType, action)
            .history(history).facts(facts);
        for (Map.Entry<Class<? extends FactType<?>>, FactSource> source : factSources.entrySet()) {
            context.factSource(source.getKey(), source.getValue());
        }
        RuleEvaluationContext input = context.build();
        List<RuleMatch> controlMatches = new ArrayList<RuleMatch>();
        List<SecurityAlert> raised = new ArrayList<SecurityAlert>();
        List<SecurityAlert> controlAlerts = new ArrayList<SecurityAlert>();
        for (DetectionRule<? extends RuleType> rule : rules) {
            RuleMode ruleMode = rule.definition().getMode();
            if (ruleMode == RuleMode.DISABLED) {
                continue;
            }
            if (ineligibleRuleTypes.contains(rule.type())) {
                continue;
            }
            RuleEvaluationContext.Evaluation evaluation = evaluate(input, rule);
            if (!evaluation.getMatch().isPresent()) {
                continue;
            }
            RuleMatch match = evaluation.getMatch().get();
            if (whitelist.isActive(match.getRuleId(), match.getSubject(), Instant.now(clock))) {
                continue;
            }
            if (ruleMode == RuleMode.OBSERVE) {
                observations.save(RuleObservation.of(UUID.randomUUID().toString(), match.getRuleId(),
                    event.getEventId(), match.getSubject(), Instant.now(clock)));
                continue;
            }
            SecurityAlert alert = raise(match, event);
            raised.add(alert);
            if (ruleMode == RuleMode.ENFORCE) {
                controlMatches.add(match);
                controlAlerts.add(alert);
            }
        }
        return new EvaluationResult(controlMatches, controlAlerts, raised);
    }

    private static List<SecurityEvent> canonicalHistory(SecurityEvent current, List<SecurityEvent> persisted) {
        List<SecurityEvent> history = new ArrayList<SecurityEvent>(persisted.size() + 1);
        boolean replaced = false;
        for (SecurityEvent candidate : persisted) {
            if (current.getEventId().equals(candidate.getEventId())) {
                if (!replaced) {
                    history.add(current);
                    replaced = true;
                }
            } else {
                history.add(candidate);
            }
        }
        if (!replaced) {
            history.add(current);
        }
        java.util.Collections.sort(history, new java.util.Comparator<SecurityEvent>() {
            @Override
            public int compare(SecurityEvent left, SecurityEvent right) {
                int time = left.getOccurredAt().compareTo(right.getOccurredAt());
                return time != 0 ? time : left.getEventId().compareTo(right.getEventId());
            }
        });
        return history;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static RuleEvaluationContext.Evaluation evaluate(RuleEvaluationContext context,
            DetectionRule<? extends RuleType> rule) {
        return context.evaluate((DetectionRule) rule);
    }

    private SecurityAlert raise(RuleMatch match, SecurityEvent event) {
        Optional<SecurityAlert> existing = alerts.findOpen(match.fingerprint());
        Instant now = Instant.now(clock);
        SecurityAlert alert = existing.isPresent() ? existing.get().observed(now)
            : SecurityAlert.open(UUID.randomUUID().toString(), match, now);
        alerts.save(alert);
        alerts.linkEvent(alert.getAlertId(), event.getEventId());
        return alert;
    }

    private void executeControls(EvaluationResult result) {
        for (int index = 0; index < result.controlMatches.size(); index++) {
            RuleMatch match = result.controlMatches.get(index);
            SecurityAlert alert = result.controlAlerts.get(index);
            for (ControlActionType action : match.getActions()) {
                if (action == ControlActionType.RECORD) {
                    continue;
                }
                controls.execute(new ControlCommand(alert.getAlertId() + ":" + action, alert.getAlertId(),
                    match.getSubject(), action, Instant.now(clock).plus(match.getControlTtl()), match.getRuleId()));
            }
        }
    }

    private static final class EvaluationResult {
        private final List<RuleMatch> controlMatches;
        private final List<SecurityAlert> controlAlerts;
        private final List<SecurityAlert> alerts;
        private EvaluationResult(List<RuleMatch> controlMatches, List<SecurityAlert> controlAlerts,
                List<SecurityAlert> alerts) {
            this.controlMatches = controlMatches;
            this.controlAlerts = controlAlerts;
            this.alerts = alerts;
        }
    }
}

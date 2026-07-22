package io.github.jasper.monitoring.core;

import io.github.jasper.monitoring.api.ControlActionType;
import io.github.jasper.monitoring.api.MonitoringMode;
import io.github.jasper.monitoring.api.SecurityEventDraft;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Objects;
import java.util.UUID;

/**
 * Coordinates event persistence, deterministic detection, alerting, and optional enforcement.
 * In {@code ENFORCE} mode construction fails without at least one host {@link ControlHandler}.
 */
public final class DefaultSecurityMonitor implements SecurityMonitor {
    private final String systemId;
    private final Clock clock;
    private final MonitoringRepository repository;
    private final List<DetectionRule> rules;
    private final MonitoringMode mode;
    private final DefaultAlertService alertService;
    private final DefaultControlService controlService;

    /**
     * @param systemId stable identifier stamped onto all persisted events
     * @param clock source of server-side timestamps
     * @param repository persistence port for monitoring state
     * @param rules deterministic rules evaluated for every event
     * @param mode observation only or host-control enforcement mode
     * @param handlers host control action implementations; required in enforce mode
     * @param notifications best-effort alert notification channel
     * @throws IllegalStateException if enforce mode is configured without a host control handler
     */
    public DefaultSecurityMonitor(String systemId, Clock clock, MonitoringRepository repository, List<DetectionRule> rules,
                                  MonitoringMode mode, ControlHandlerRegistry handlers, NotificationChannel notifications) {
        if (systemId == null || systemId.trim().isEmpty()) { throw new IllegalArgumentException("systemId is required"); }
        this.systemId = systemId;
        this.clock = Objects.requireNonNull(clock, "clock");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.rules = new ArrayList<DetectionRule>(rules);
        this.mode = Objects.requireNonNull(mode, "mode");
        if (this.mode == MonitoringMode.ENFORCE && handlers.isEmpty()) {
            throw new IllegalStateException("ENFORCE mode requires at least one host ControlHandler");
        }
        this.alertService = new DefaultAlertService(repository, notifications, clock);
        this.controlService = new DefaultControlService(repository, handlers, clock);
    }

    /**
     * Persists an event before evaluating rules, so matching logic always includes the current observation.
     * Control failures are represented in the returned outcome and do not throw into host business processing.
     */
    @Override
    public MonitoringOutcome record(SecurityEventDraft draft) {
        SecurityEvent event = SecurityEvent.from(draft, systemId, UUID.randomUUID().toString(), Instant.now(clock));
        repository.saveEvent(event);
        List<SecurityEvent> history = repository.findEventsSince(event.getOccurredAt().minus(Duration.ofDays(1)));
        List<RuleMatch> matches = new ArrayList<RuleMatch>();
        List<SecurityAlert> alerts = new ArrayList<SecurityAlert>();
        List<ControlExecution> controls = new ArrayList<ControlExecution>();
        for (DetectionRule rule : rules) {
            Optional<RuleMatch> match = rule.evaluate(event, history);
            if (!match.isPresent()) { continue; }
            RuleMatch value = match.get();
            if (repository.isWhitelisted(rule.getRuleId(), value.getSubject(), Instant.now(clock))) { continue; }
            matches.add(value);
            SecurityAlert alert = alertService.raise(value, event);
            alerts.add(alert);
            if (mode == MonitoringMode.ENFORCE) {
                for (ControlActionType action : value.getActions()) {
                    if (action == ControlActionType.RECORD) { continue; }
                    ControlCommand command = new ControlCommand(alert.getAlertId() + ":" + action, alert.getAlertId(),
                        value.getSubject(), action, Instant.now(clock).plus(value.getControlTtl()));
                    controls.add(controlService.execute(command));
                }
            }
        }
        return new MonitoringOutcome(event, matches, alerts, controls);
    }

    /** @return the control service used by this monitor, useful for explicit host integrations */
    public DefaultControlService getControlService() { return controlService; }
}

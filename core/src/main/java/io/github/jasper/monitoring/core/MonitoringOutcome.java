package io.github.jasper.monitoring.core;

import io.github.jasper.monitoring.api.ControlActionType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable result of recording one event, including all matches, alerts, and attempted controls. */
public final class MonitoringOutcome {
    private final SecurityEvent event;
    private final List<RuleMatch> matches;
    private final List<SecurityAlert> alerts;
    private final List<ControlExecution> controls;
    /**
     * @param event server-stamped event that was persisted
     * @param matches rules matched by the event
     * @param alerts alerts created or refreshed from the matches
     * @param controls control outcomes attempted in enforce mode
     */
    public MonitoringOutcome(SecurityEvent event, List<RuleMatch> matches, List<SecurityAlert> alerts,
                             List<ControlExecution> controls) {
        this.event = event;
        this.matches = Collections.unmodifiableList(new ArrayList<RuleMatch>(matches));
        this.alerts = Collections.unmodifiableList(new ArrayList<SecurityAlert>(alerts));
        this.controls = Collections.unmodifiableList(new ArrayList<ControlExecution>(controls));
    }
    public SecurityEvent getEvent() { return event; }
    public List<RuleMatch> getMatches() { return matches; }
    public List<SecurityAlert> getAlerts() { return alerts; }
    public List<ControlExecution> getControls() { return controls; }
    /** @return whether any matched rule recommended {@code action}, independent of enforcement mode */
    public boolean hasRisk(ControlActionType action) {
        for (RuleMatch match : matches) {
            if (match.getActions().contains(action)) { return true; }
        }
        return false;
    }
}

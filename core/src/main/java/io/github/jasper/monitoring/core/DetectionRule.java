package io.github.jasper.monitoring.core;

import java.util.List;
import java.util.Optional;

/** Deterministic and explainable rule evaluated against one event and its time-window history. */
public interface DetectionRule {
    /** @return stable rule identifier used in alerts, controls, and whitelist entries */
    String getRuleId();

    /**
     * Evaluates one event against the supplied bounded history.
     *
     * @param event event currently being recorded
     * @param history chronological event history available to the rule
     * @return a match with risk and suggested actions, or empty when the event does not violate this rule
     */
    Optional<RuleMatch> evaluate(SecurityEvent event, List<SecurityEvent> history);
}

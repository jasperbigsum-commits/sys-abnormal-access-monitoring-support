package io.github.jasper.monitoring.spring.support;

import io.github.jasper.monitoring.api.action.ActionDecision;
import io.github.jasper.monitoring.api.fact.ActionFacts;

/** Evaluates one immutable snapshot of completed runtime facts. */
@FunctionalInterface
public interface MonitoringCheckpoint {
    ActionDecision decide(ActionFacts facts);
}

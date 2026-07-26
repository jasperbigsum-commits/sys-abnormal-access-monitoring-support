package io.github.jasper.monitoring.core.port;

import io.github.jasper.monitoring.core.domain.rule.RuleObservation;

/** Append-only persistence port for observe-only rule matches. */
public interface RuleObservationRepository {
    void save(RuleObservation observation);
}

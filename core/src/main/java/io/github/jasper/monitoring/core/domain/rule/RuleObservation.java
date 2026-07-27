package io.github.jasper.monitoring.core.domain.rule;

import java.time.Instant;
import java.util.Objects;

/** Immutable evidence retained for a matching observe-only rule. */
public final class RuleObservation {
    private final String observationId;
    private final String ruleId;
    private final String eventId;
    private final String subject;
    private final Instant observedAt;

    private RuleObservation(String observationId, String ruleId, String eventId, String subject,
            Instant observedAt) {
        this.observationId = required(observationId, "observationId");
        this.ruleId = required(ruleId, "ruleId");
        this.eventId = required(eventId, "eventId");
        this.subject = required(subject, "subject");
        this.observedAt = Objects.requireNonNull(observedAt, "observedAt");
    }

    /** Creates either a new observation or an exact reconstruction from persistence. */
    public static RuleObservation of(String observationId, String ruleId, String eventId,
            String subject, Instant observedAt) {
        return new RuleObservation(observationId, ruleId, eventId, subject, observedAt);
    }

    public String getObservationId() { return observationId; }
    public String getRuleId() { return ruleId; }
    public String getEventId() { return eventId; }
    public String getSubject() { return subject; }
    public Instant getObservedAt() { return observedAt; }

    private static String required(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.trim().isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}

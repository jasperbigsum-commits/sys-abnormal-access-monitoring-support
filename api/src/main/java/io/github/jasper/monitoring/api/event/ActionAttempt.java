package io.github.jasper.monitoring.api.event;

import io.github.jasper.monitoring.api.action.ActionDecision;
import io.github.jasper.monitoring.api.action.ActionType;
import io.github.jasper.monitoring.api.fact.ActionFacts;
import java.util.Objects;
import java.util.UUID;

/** Lifecycle aggregate for one monitored action attempt and its synchronous checkpoint. */
public final class ActionAttempt {
    public enum Status {
        CREATED, FACTS_READY, DECIDED_ALLOWED, DECIDED_BLOCKED,
        COMPLETED_SUCCESS, COMPLETED_FAILURE, COMPLETED_DENIED
    }

    private final String attemptId;
    private final Class<? extends ActionType> actionType;
    private Status status = Status.CREATED;
    private ActionFacts facts;
    private ActionDecision decision;

    private ActionAttempt(Class<? extends ActionType> actionType) {
        this.attemptId = UUID.randomUUID().toString();
        this.actionType = Objects.requireNonNull(actionType, "actionType");
    }

    public static ActionAttempt start(Class<? extends ActionType> actionType) {
        return new ActionAttempt(actionType);
    }

    public synchronized void factsReady(ActionFacts facts) {
        require(Status.CREATED);
        this.facts = Objects.requireNonNull(facts, "facts");
        status = Status.FACTS_READY;
    }

    public synchronized void decided(ActionDecision decision) {
        require(Status.FACTS_READY);
        this.decision = Objects.requireNonNull(decision, "decision");
        status = decision.isAllowed() ? Status.DECIDED_ALLOWED : Status.DECIDED_BLOCKED;
    }

    public synchronized void complete(ActionOutcome outcome) {
        Objects.requireNonNull(outcome, "outcome");
        if (status == Status.DECIDED_ALLOWED) {
            status = outcome.getResult() == io.github.jasper.monitoring.api.SecurityEventResult.SUCCESS
                ? Status.COMPLETED_SUCCESS : Status.COMPLETED_FAILURE;
        } else if (status == Status.DECIDED_BLOCKED
                && outcome.getResult() == io.github.jasper.monitoring.api.SecurityEventResult.DENIED) {
            status = Status.COMPLETED_DENIED;
        } else if (status != Status.COMPLETED_SUCCESS && status != Status.COMPLETED_FAILURE
                && status != Status.COMPLETED_DENIED) {
            throw new IllegalStateException("Action attempt must be decided before completion");
        }
    }

    public String getAttemptId() { return attemptId; }
    public Class<? extends ActionType> getActionType() { return actionType; }
    public synchronized Status getStatus() { return status; }
    public synchronized ActionFacts getFacts() { return facts; }
    public synchronized ActionDecision getDecision() { return decision; }

    private void require(Status expected) {
        if (status != expected) {
            throw new IllegalStateException("Expected action attempt status " + expected + " but was " + status);
        }
    }
}

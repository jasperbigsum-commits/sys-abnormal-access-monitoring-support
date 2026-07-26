package io.github.jasper.monitoring.api.action;

/** Defines how monitoring infrastructure failures affect the monitored action. */
public enum ActionFailurePolicy {
    /** Record the observation issue without changing the business invocation. */
    OBSERVE_ONLY,
    /** Reject the business invocation when required monitoring cannot complete. */
    FAIL_CLOSED;

    /** @return whether this policy is at least as strict as the supplied policy */
    public boolean isAtLeast(ActionFailurePolicy other) {
        return ordinal() >= other.ordinal();
    }

    static ActionFailurePolicy strictest(ActionFailurePolicy first, ActionFailurePolicy second) {
        return first.isAtLeast(second) ? first : second;
    }
}

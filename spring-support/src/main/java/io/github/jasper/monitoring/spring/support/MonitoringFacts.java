package io.github.jasper.monitoring.spring.support;

import io.github.jasper.monitoring.api.fact.ActionFacts;
import io.github.jasper.monitoring.api.action.ActionDecision;
import io.github.jasper.monitoring.api.action.ActionType;
import io.github.jasper.monitoring.api.event.ActionAttempt;
import io.github.jasper.monitoring.api.event.ActionOutcome;
import io.github.jasper.monitoring.api.fact.FactType;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Logger;

/** Adds server-derived facts to the innermost active {@code @MonitorAction} invocation. */
public final class MonitoringFacts {
    private static final Logger LOGGER = Logger.getLogger(MonitoringFacts.class.getName());
    private static final ThreadLocal<Deque<ScopeState>> SCOPES =
        new ThreadLocal<Deque<ScopeState>>();

    private MonitoringFacts() {
    }

    /**
     * Adds one typed fact to the current monitored action.
     *
     * @return {@code true} when an active scope accepted the fact; {@code false} when no scope exists
     */
    public static <T> boolean put(Class<? extends FactType<T>> factType, T value) {
        Objects.requireNonNull(factType, "factType");
        Objects.requireNonNull(value, "value");
        Deque<ScopeState> scopes = SCOPES.get();
        if (scopes == null || scopes.isEmpty()) {
            LOGGER.warning("MonitoringFacts.put ignored because there is no active monitored action scope");
            return false;
        }
        scopes.peek().put(factType, value);
        return true;
    }

    static ScopeState openScope() {
        return openScope(null);
    }

    static ScopeState openScope(MonitoringCheckpoint checkpoint) {
        return openScope(null, checkpoint);
    }

    static ScopeState openScope(Class<? extends ActionType> actionType, MonitoringCheckpoint checkpoint) {
        Deque<ScopeState> scopes = SCOPES.get();
        if (scopes == null) {
            scopes = new ArrayDeque<>();
            SCOPES.set(scopes);
        }
        ScopeState state = new ScopeState(actionType == null ? null : ActionAttempt.start(actionType), checkpoint);
        scopes.push(state);
        return state;
    }

    static void closeScope(ScopeState state) {
        Deque<ScopeState> scopes = SCOPES.get();
        if (scopes == null || scopes.isEmpty() || scopes.peek() != state) {
            throw new IllegalStateException("Monitoring fact scopes must close in stack order");
        }
        scopes.pop();
        if (scopes.isEmpty()) SCOPES.remove();
    }

    static ActionDecision checkpoint() {
        Deque<ScopeState> scopes = SCOPES.get();
        if (scopes == null || scopes.isEmpty()) {
            throw new IllegalStateException("Monitoring checkpoint requires an active monitored action scope");
        }
        return scopes.peek().checkpoint();
    }

    static final class ScopeState {
        private final MonitoringCheckpoint checkpoint;
        private final ActionAttempt attempt;
        private final Map<Class<? extends FactType<?>>, Object> values =
            new LinkedHashMap<Class<? extends FactType<?>>, Object>();
        private ActionDecision decision;

        ScopeState(ActionAttempt attempt, MonitoringCheckpoint checkpoint) {
            this.attempt = attempt;
            this.checkpoint = checkpoint;
        }

        ActionAttempt attempt() {
            if (attempt == null) throw new IllegalStateException("Action attempt is not configured for this scope");
            return attempt;
        }

        <T> void put(Class<? extends FactType<T>> factType, T value) {
            if (decision != null) {
                throw new IllegalStateException("Facts are frozen after the monitoring checkpoint");
            }
            if (values.containsKey(factType)) {
                throw new IllegalStateException("Fact was already added to the current monitored action: "
                    + factType.getName());
            }
            values.put(factType, value);
        }

        ActionFacts snapshot() {
            ActionFacts.Builder builder = ActionFacts.builder();
            for (Map.Entry<Class<? extends FactType<?>>, Object> entry : values.entrySet()) {
                putRaw(builder, entry.getKey(), entry.getValue());
            }
            return builder.build();
        }

        ActionDecision checkpoint() {
            if (checkpoint == null) {
                throw new IllegalStateException("Monitoring checkpoint is not configured for this scope");
            }
            if (decision == null) {
                ActionFacts facts = snapshot();
                if (attempt != null) attempt.factsReady(facts);
                decision = Objects.requireNonNull(checkpoint.decide(facts), "checkpoint decision");
                if (attempt != null) attempt.decided(decision);
            }
            return decision;
        }

        void complete(ActionOutcome outcome) {
            if (attempt != null && attempt.getDecision() != null) attempt.complete(outcome);
        }

        @SuppressWarnings({"rawtypes", "unchecked"})
        private static void putRaw(ActionFacts.Builder builder,
                Class<? extends FactType<?>> factType, Object value) {
            builder.put((Class) factType, value);
        }
    }
}

package io.github.jasper.monitoring.spring.support;

import io.github.jasper.monitoring.api.fact.ActionFacts;
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
        Deque<ScopeState> scopes = SCOPES.get();
        if (scopes == null) {
            scopes = new ArrayDeque<ScopeState>();
            SCOPES.set(scopes);
        }
        ScopeState state = new ScopeState();
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

    static final class ScopeState {
        private final Map<Class<? extends FactType<?>>, Object> values =
            new LinkedHashMap<Class<? extends FactType<?>>, Object>();

        <T> void put(Class<? extends FactType<T>> factType, T value) {
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

        @SuppressWarnings({"rawtypes", "unchecked"})
        private static void putRaw(ActionFacts.Builder builder,
                Class<? extends FactType<?>> factType, Object value) {
            builder.put((Class) factType, value);
        }
    }
}

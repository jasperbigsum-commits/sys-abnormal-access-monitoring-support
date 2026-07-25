package io.github.jasper.monitoring.api.fact;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Immutable values keyed by their typed fact classes. */
public final class ActionFacts {
    private final Map<Class<? extends FactType<?>>, Object> values;

    private ActionFacts(Map<Class<? extends FactType<?>>, Object> values) {
        this.values = Collections.unmodifiableMap(
            new LinkedHashMap<Class<? extends FactType<?>>, Object>(values));
    }

    /** @return a builder for typed action facts */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Returns a value using the compile-time type declared by its fact token.
     *
     * @param factType fact token
     * @param <T> fact value type
     * @return the stored value, or {@code null} when absent
     */
    @SuppressWarnings("unchecked")
    public <T> T get(Class<? extends FactType<T>> factType) {
        Objects.requireNonNull(factType, "factType");
        return (T) values.get(factType);
    }

    /** @return an immutable view of all class-keyed fact values */
    public Map<Class<? extends FactType<?>>, Object> asMap() {
        return values;
    }

    /** Builder that preserves the compile-time association between fact tokens and values. */
    public static final class Builder {
        private final Map<Class<? extends FactType<?>>, Object> values =
            new LinkedHashMap<Class<? extends FactType<?>>, Object>();

        private Builder() {
        }

        /**
         * Adds a fact with compile-time value type checking.
         *
         * @param factType fact token
         * @param value non-null fact value
         * @param <T> fact value type
         * @return this builder
         */
        public <T> Builder put(Class<? extends FactType<T>> factType, T value) {
            Objects.requireNonNull(factType, "factType");
            Objects.requireNonNull(value, "value");
            values.put(factType, value);
            return this;
        }

        /** @return an immutable snapshot of the accumulated facts */
        public ActionFacts build() {
            return new ActionFacts(values);
        }

    }
}

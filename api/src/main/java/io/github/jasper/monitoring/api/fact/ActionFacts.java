package io.github.jasper.monitoring.api.fact;

import java.lang.reflect.InvocationTargetException;
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

    /** Builder that validates every value against its fact type. */
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
            return putValidated(factType, value);
        }

        /**
         * Adds a fact supplied by a framework adapter with runtime type checking.
         *
         * @param factType fact token
         * @param value non-null fact value
         * @return this builder
         */
        public Builder putRaw(Class<? extends FactType<?>> factType, Object value) {
            return putValidated(factType, value);
        }

        /** @return an immutable snapshot of the accumulated facts */
        public ActionFacts build() {
            return new ActionFacts(values);
        }

        private Builder putValidated(Class<? extends FactType<?>> factType, Object value) {
            Objects.requireNonNull(factType, "factType");
            Objects.requireNonNull(value, "value");
            Class<?> valueType = Objects.requireNonNull(newFactType(factType).valueType(), "valueType");
            if (!valueType.isInstance(value)) {
                throw new IllegalArgumentException("Fact value for " + factType.getName()
                    + " must be an instance of " + valueType.getName());
            }
            values.put(factType, value);
            return this;
        }

        private static FactType<?> newFactType(Class<? extends FactType<?>> factType) {
            try {
                return factType.getDeclaredConstructor().newInstance();
            } catch (InstantiationException | IllegalAccessException | InvocationTargetException
                     | NoSuchMethodException exception) {
                throw new IllegalArgumentException("Fact type must have an accessible no-argument constructor: "
                    + factType.getName(), exception);
            }
        }
    }
}

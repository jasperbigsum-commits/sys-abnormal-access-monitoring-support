package io.github.jasper.monitoring.api.fact;

import java.lang.reflect.Array;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Immutable values keyed by their typed fact classes. */
public final class ActionFacts {
    private static final ClassValue<Class<?>> VALUE_TYPES = new ClassValue<Class<?>>() {
        @Override
        protected Class<?> computeValue(Class<?> factType) {
            Type valueType = findFactValueType(factType,
                Collections.<TypeVariable<?>, Type>emptyMap());
            Class<?> valueClass = toClass(valueType);
            if (valueClass == null) {
                throw new IllegalArgumentException("Fact type must declare a concrete value type: "
                    + factType.getName());
            }
            return valueClass;
        }
    };

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
            Class<?> valueType = VALUE_TYPES.get(factType);
            if (!valueType.isInstance(value)) {
                throw new IllegalArgumentException("Fact value for " + factType.getName()
                    + " must be an instance of " + valueType.getName());
            }
            values.put(factType, value);
            return this;
        }
    }

    private static Type findFactValueType(Type candidate, Map<TypeVariable<?>, Type> inheritedBindings) {
        Class<?> rawType;
        Map<TypeVariable<?>, Type> bindings = new HashMap<TypeVariable<?>, Type>(inheritedBindings);
        if (candidate instanceof ParameterizedType) {
            ParameterizedType parameterized = (ParameterizedType) candidate;
            rawType = (Class<?>) parameterized.getRawType();
            TypeVariable<?>[] parameters = rawType.getTypeParameters();
            Type[] arguments = parameterized.getActualTypeArguments();
            for (int index = 0; index < parameters.length; index++) {
                bindings.put(parameters[index], resolve(arguments[index], inheritedBindings));
            }
        } else if (candidate instanceof Class<?>) {
            rawType = (Class<?>) candidate;
        } else {
            return null;
        }

        if (rawType == FactType.class) {
            return resolve(rawType.getTypeParameters()[0], bindings);
        }
        for (Type implementedType : rawType.getGenericInterfaces()) {
            Type valueType = findFactValueType(implementedType, bindings);
            if (valueType != null) {
                return valueType;
            }
        }
        Type parentType = rawType.getGenericSuperclass();
        return parentType == null ? null : findFactValueType(parentType, bindings);
    }

    private static Type resolve(Type type, Map<TypeVariable<?>, Type> bindings) {
        Type resolved = type;
        while (resolved instanceof TypeVariable<?> && bindings.containsKey(resolved)) {
            Type replacement = bindings.get(resolved);
            if (replacement == resolved) {
                break;
            }
            resolved = replacement;
        }
        return resolved;
    }

    private static Class<?> toClass(Type type) {
        if (type instanceof Class<?>) {
            return (Class<?>) type;
        }
        if (type instanceof ParameterizedType) {
            return toClass(((ParameterizedType) type).getRawType());
        }
        if (type instanceof GenericArrayType) {
            Class<?> componentType = toClass(((GenericArrayType) type).getGenericComponentType());
            return componentType == null ? null : Array.newInstance(componentType, 0).getClass();
        }
        if (type instanceof WildcardType) {
            Type[] upperBounds = ((WildcardType) type).getUpperBounds();
            return upperBounds.length == 0 ? null : toClass(upperBounds[0]);
        }
        return null;
    }
}

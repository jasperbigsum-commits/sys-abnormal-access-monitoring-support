package io.github.jasper.monitoring.spring.support;

import io.github.jasper.monitoring.api.fact.FactCatalog;
import io.github.jasper.monitoring.api.fact.ActionFacts;
import io.github.jasper.monitoring.api.fact.FactDefinition;
import io.github.jasper.monitoring.api.fact.FactSource;
import io.github.jasper.monitoring.api.fact.FactType;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Extracts typed method-parameter facts through a deliberately restricted property grammar. */
public final class ActionFactExtractor {
    private static final Pattern PATH = Pattern.compile(
        "[A-Za-z_$][A-Za-z0-9_$]*(?:\\.[A-Za-z_$][A-Za-z0-9_$]*|\\[[0-9]+\\])*"
    );
    private static final Pattern PART = Pattern.compile("([A-Za-z_$][A-Za-z0-9_$]*)(.*)");
    private static final Pattern INDEX = Pattern.compile("\\[([0-9]+)\\]");

    private final FactCatalog facts;

    public ActionFactExtractor(FactCatalog facts) {
        this.facts = Objects.requireNonNull(facts, "facts");
        if (!facts.isFrozen()) throw new IllegalArgumentException("Fact catalog must be frozen");
    }

    /** Extracts and normalizes one fact value; a null path value remains absent. */
    public Object extract(Object root, String path, Class<? extends FactType<?>> factType) {
        Objects.requireNonNull(path, "path");
        FactDefinition<?> definition = facts.require(factType);
        Object value = resolve(root, path);
        return value == null ? null : definition.validateRaw(value);
    }

    /** Extracts every non-null fact declared by a validated method binding. */
    public ActionFacts extract(MonitorActionContractValidator.MethodBinding binding, Object[] arguments) {
        Objects.requireNonNull(binding, "binding");
        Objects.requireNonNull(arguments, "arguments");
        ActionFacts.Builder result = ActionFacts.builder();
        for (MonitorActionContractValidator.ParameterFact fact : binding.getFacts()) {
            if (fact.getParameterIndex() >= arguments.length) {
                throw new IllegalArgumentException("ActionFact parameter index is out of range");
            }
            Object value = extract(arguments[fact.getParameterIndex()], fact.getPath(), fact.getFactType());
            if (value != null) put(result, fact.getFactType(), value);
        }
        return result.build();
    }

    /** Validates completeness, source ownership, and values for one action invocation. */
    public ActionFacts validate(MonitorActionContractValidator.MethodBinding binding,
            ActionFacts actionFacts, Map<Class<? extends FactType<?>>, FactSource> sources) {
        Objects.requireNonNull(binding, "binding");
        Objects.requireNonNull(actionFacts, "actionFacts");
        Objects.requireNonNull(sources, "sources");
        if (!actionFacts.asMap().keySet().equals(sources.keySet()) || sources.containsValue(null)) {
            throw new IllegalStateException("Every action fact must have exactly one source");
        }
        for (Class<? extends FactType<?>> required : binding.getAction().getRequiredFacts()) {
            if (!actionFacts.asMap().containsKey(required)) {
                throw new IllegalStateException("Required action fact is missing: " + required.getName());
            }
        }
        ActionFacts.Builder validated = ActionFacts.builder();
        for (Map.Entry<Class<? extends FactType<?>>, Object> entry : actionFacts.asMap().entrySet()) {
            Class<? extends FactType<?>> factType = entry.getKey();
            if (!binding.getAction().getRequiredFacts().contains(factType)
                    && !binding.getAction().getOptionalFacts().contains(factType)) {
                throw new IllegalStateException("Fact is not declared by action: " + factType.getName());
            }
            FactSource source = sources.get(factType);
            FactDefinition<?> definition = facts.require(factType);
            if (!binding.getAction().getAllowedSources(factType).contains(source)
                    || !definition.allows(source)) {
                throw new IllegalStateException("Fact source is not approved: " + factType.getName());
            }
            put(validated, factType, definition.validateRaw(entry.getValue()));
        }
        return validated.build();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void put(ActionFacts.Builder builder, Class<? extends FactType<?>> type, Object value) {
        builder.put((Class) type, value);
    }

    static void validatePath(String path) {
        Objects.requireNonNull(path, "path");
        if (!path.isEmpty() && !PATH.matcher(path).matches()) {
            throw new IllegalArgumentException("ActionFact path uses unsupported syntax");
        }
    }

    private static Object resolve(Object root, String path) {
        validatePath(path);
        Object current = root;
        if (path.isEmpty()) return current;
        for (String part : path.split("\\.")) {
            if (current == null) return null;
            Matcher partMatcher = PART.matcher(part);
            if (!partMatcher.matches()) throw new IllegalArgumentException("Invalid ActionFact path");
            current = property(current, partMatcher.group(1));
            Matcher indexes = INDEX.matcher(partMatcher.group(2));
            int consumed = 0;
            while (indexes.find()) {
                if (indexes.start() != consumed) throw new IllegalArgumentException("Invalid ActionFact index");
                if (current == null) return null;
                current = index(current, Integer.parseInt(indexes.group(1)));
                consumed = indexes.end();
            }
            if (consumed != partMatcher.group(2).length()) {
                throw new IllegalArgumentException("Invalid ActionFact index");
            }
        }
        return current;
    }

    private static Object property(Object target, String name) {
        if ("class".equals(name) || target instanceof Map) {
            throw new IllegalArgumentException("ActionFact path cannot access this property");
        }
        String suffix = Character.toUpperCase(name.charAt(0)) + name.substring(1);
        Method getter = publicGetter(target.getClass(), "get" + suffix);
        if (getter == null) getter = publicGetter(target.getClass(), "is" + suffix);
        try {
            if (getter != null) return getter.invoke(target);
            Field field = target.getClass().getField(name);
            if (!Modifier.isPublic(field.getModifiers())) {
                throw new IllegalArgumentException("ActionFact field is not public");
            }
            return field.get(target);
        } catch (NoSuchFieldException e) {
            throw new IllegalArgumentException("ActionFact property is not publicly readable");
        } catch (IllegalAccessException e) {
            throw new IllegalArgumentException("ActionFact property is not accessible");
        } catch (InvocationTargetException e) {
            throw new IllegalArgumentException("ActionFact getter failed");
        }
    }

    private static Method publicGetter(Class<?> type, String name) {
        try {
            Method method = type.getMethod(name);
            return Modifier.isPublic(method.getModifiers()) && method.getParameterTypes().length == 0
                ? method : null;
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    private static Object index(Object value, int index) {
        if (value.getClass().isArray()) {
            if (index >= Array.getLength(value)) throw new IllegalArgumentException("ActionFact index is out of range");
            return Array.get(value, index);
        }
        if (value instanceof List) {
            List<?> list = (List<?>) value;
            if (index >= list.size()) throw new IllegalArgumentException("ActionFact index is out of range");
            return list.get(index);
        }
        throw new IllegalArgumentException("ActionFact index requires an array or List");
    }
}

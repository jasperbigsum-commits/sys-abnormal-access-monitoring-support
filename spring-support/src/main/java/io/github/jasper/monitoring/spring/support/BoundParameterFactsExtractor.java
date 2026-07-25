package io.github.jasper.monitoring.spring.support;

import io.github.jasper.monitoring.api.EventFactSource;
import io.github.jasper.monitoring.api.EventInputIssue;
import io.github.jasper.monitoring.api.EventInputIssueCode;
import io.github.jasper.monitoring.api.MonitorActionAttribute;
import io.github.jasper.monitoring.api.MonitorActionAttributeTarget;
import io.github.jasper.monitoring.api.MonitorActionDefinition;
import io.github.jasper.monitoring.api.MonitorActionFacts;
import io.github.jasper.monitoring.api.SecurityFieldSanitizer;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Safely extracts approved facts from annotated method parameters. */
public final class BoundParameterFactsExtractor {
    /** Stable pseudo rule used only for annotation collection diagnostics. */
    public static final String DIAGNOSTIC_RULE_ID = "MONITOR-ACTION";

    /**
     * Extracts approved facts while retaining the original no-diagnostics API.
     *
     * @param method annotated controller method
     * @param arguments runtime method arguments
     * @return immutable dynamic facts
     */
    public MonitorActionFacts extract(Method method, Object[] arguments) {
        return extractWithDiagnostics(method, arguments).getFacts();
    }

    /**
     * Extracts approved facts and stable diagnostics for annotations that could not be resolved.
     *
     * <p>The result intentionally excludes parameter paths, values and reflection failure details. A failed
     * extraction remains observational and never throws into the controller invocation.</p>
     *
     * @param method annotated controller method
     * @param arguments runtime method arguments
     * @return immutable facts and stable input-quality issues
     */
    public ExtractionResult extractWithDiagnostics(Method method, Object[] arguments) {
        MonitorActionFacts.Builder facts = MonitorActionFacts.builder();
        List<EventInputIssue> issues = new ArrayList<EventInputIssue>();
        if (method == null || arguments == null) {
            return new ExtractionResult(facts.build(), issues);
        }
        java.lang.reflect.Parameter[] parameters = method.getParameters();
        for (int index = 0; index < parameters.length; index++) {
            Object argument = index < arguments.length ? arguments[index] : null;
            for (MonitorActionAttribute attribute : parameters[index].getAnnotationsByType(MonitorActionAttribute.class)) {
                extract(attribute, argument, facts, issues);
            }
        }
        return new ExtractionResult(facts.build(), issues);
    }

    private static void extract(MonitorActionAttribute attribute, Object argument, MonitorActionFacts.Builder facts,
                                List<EventInputIssue> issues) {
        try {
            MonitorActionDefinition.validateParameterAttribute(attribute);
            Resolution resolution = resolve(argument, attribute.path());
            if (!resolution.isResolved()) {
                issues.add(issue(attribute, EventInputIssueCode.UNRESOLVED_PARAMETER_PATH));
                return;
            }
            String text = safeScalarText(resolution.getValue());
            if (text == null) {
                issues.add(issue(attribute, EventInputIssueCode.INVALID_PARAMETER_VALUE));
                return;
            }
            merge(facts, contribution(attribute, text));
        } catch (RuntimeException ignored) {
            // Annotation and reflection failures are observational only and have a stable diagnostic.
            issues.add(issue(attribute, EventInputIssueCode.INVALID_PARAMETER_VALUE));
        }
    }

    private static EventInputIssue issue(MonitorActionAttribute attribute, EventInputIssueCode code) {
        return EventInputIssue.of(DIAGNOSTIC_RULE_ID, factName(attribute), code, EventFactSource.METHOD_PARAMETER);
    }

    private static String factName(MonitorActionAttribute attribute) {
        if (attribute.target() == MonitorActionAttributeTarget.RESOURCE_ID) {
            return "resourceId";
        }
        if (attribute.target() == MonitorActionAttributeTarget.ORG_SCOPE) {
            return "orgScope";
        }
        return "attribute";
    }

    private static MonitorActionFacts contribution(MonitorActionAttribute attribute, String text) {
        MonitorActionFacts.Builder facts = MonitorActionFacts.builder();
        if (attribute.target() == MonitorActionAttributeTarget.RESOURCE_ID) {
            facts.resourceId(text);
        } else if (attribute.target() == MonitorActionAttributeTarget.ORG_SCOPE) {
            facts.orgScope(text);
        } else if (attribute.target() == MonitorActionAttributeTarget.ATTRIBUTE) {
            facts.attribute(attribute.name(), text);
        }
        return facts.build();
    }

    private static String safeScalarText(Object value) {
        if (value == null) {
            return null;
        }
        Class<?> type = value.getClass();
        if (type == String.class) {
            return (String) value;
        }
        if (type == Boolean.class) {
            return Boolean.toString(((Boolean) value).booleanValue());
        }
        if (type == Character.class) {
            return Character.toString(((Character) value).charValue());
        }
        if (type == Byte.class) {
            return Byte.toString(((Byte) value).byteValue());
        }
        if (type == Short.class) {
            return Short.toString(((Short) value).shortValue());
        }
        if (type == Integer.class) {
            return Integer.toString(((Integer) value).intValue());
        }
        if (type == Long.class) {
            return Long.toString(((Long) value).longValue());
        }
        if (type == Float.class) {
            return Float.toString(((Float) value).floatValue());
        }
        if (type == Double.class) {
            return Double.toString(((Double) value).doubleValue());
        }
        return null;
    }

    private static void merge(MonitorActionFacts.Builder target, MonitorActionFacts source) {
        if (source.getResourceId() != null) {
            target.resourceId(source.getResourceId());
        }
        if (source.getOrgScope() != null) {
            target.orgScope(source.getOrgScope());
        }
        for (java.util.Map.Entry<String, String> attribute : source.getAttributes().entrySet()) {
            target.attribute(attribute.getKey(), attribute.getValue());
        }
    }

    private static Resolution resolve(Object root, String path) {
        if (root == null) {
            return Resolution.unresolved();
        }
        List<Segment> segments = parse(path);
        Object value = root;
        for (Segment segment : segments) {
            value = segment.index == null ? property(value, segment.name) : indexed(value, segment.index.intValue());
            if (value == null) {
                return Resolution.unresolved();
            }
        }
        return Resolution.resolved(value);
    }

    private static List<Segment> parse(String path) {
        if (path == null || path.isEmpty()) {
            return Collections.emptyList();
        }
        if (path.charAt(0) == '.') {
            throw new IllegalArgumentException("Invalid parameter path");
        }
        List<Segment> values = new ArrayList<Segment>();
        int index = 0;
        while (index < path.length()) {
            if (path.charAt(index) == '.') {
                index++;
            }
            int start = index;
            if (index >= path.length() || !identifierStart(path.charAt(index))) {
                throw new IllegalArgumentException("Invalid parameter path");
            }
            index++;
            while (index < path.length() && identifierPart(path.charAt(index))) {
                index++;
            }
            String name = path.substring(start, index);
            if ("class".equalsIgnoreCase(name)) {
                throw new IllegalArgumentException("class is not a parameter path segment");
            }
            SecurityFieldSanitizer.requireSafeAttributeKey(name);
            values.add(Segment.property(name));
            while (index < path.length() && path.charAt(index) == '[') {
                int digits = ++index;
                while (index < path.length() && Character.isDigit(path.charAt(index))) {
                    index++;
                }
                if (digits == index || index >= path.length() || path.charAt(index) != ']') {
                    throw new IllegalArgumentException("Invalid parameter path index");
                }
                values.add(Segment.index(Integer.parseInt(path.substring(digits, index))));
                index++;
            }
            if (index < path.length() && path.charAt(index) != '.') {
                throw new IllegalArgumentException("Invalid parameter path");
            }
        }
        return values;
    }

    private static Object property(Object value, String name) {
        if (value == null || value instanceof java.util.Map || value.getClass().isArray()
            || value instanceof java.util.List) {
            return null;
        }
        String suffix = Character.toUpperCase(name.charAt(0)) + name.substring(1);
        Method getter = getter(value.getClass(), suffix);
        if (getter == null) {
            return publicField(value, name);
        }
        if (!Modifier.isPublic(getter.getModifiers()) || Modifier.isStatic(getter.getModifiers())
            || getter.getParameterTypes().length != 0) {
            return null;
        }
        try {
            return getter.invoke(value);
        } catch (ReflectiveOperationException ignored) {
            return null;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static Method getter(Class<?> type, String suffix) {
        try {
            return type.getMethod("get" + suffix);
        } catch (NoSuchMethodException ignored) {
            try {
                return type.getMethod("is" + suffix);
            } catch (NoSuchMethodException missing) {
                return null;
            }
        }
    }

    private static Object publicField(Object value, String name) {
        try {
            Field field = value.getClass().getField(name);
            if (!Modifier.isPublic(field.getModifiers()) || Modifier.isStatic(field.getModifiers())) {
                return null;
            }
            return field.get(value);
        } catch (ReflectiveOperationException ignored) {
            return null;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static Object indexed(Object value, int index) {
        if (value == null || index < 0) {
            return null;
        }
        if (value.getClass().isArray()) {
            return index < Array.getLength(value) ? Array.get(value, index) : null;
        }
        if (value instanceof java.util.List) {
            List<?> list = (List<?>) value;
            return index < list.size() ? list.get(index) : null;
        }
        return null;
    }

    private static boolean identifierStart(char value) {
        return value == '_' || Character.isLetter(value);
    }

    private static boolean identifierPart(char value) {
        return identifierStart(value) || Character.isDigit(value);
    }

    /** Immutable result of one parameter-fact extraction attempt. */
    public static final class ExtractionResult {
        private final MonitorActionFacts facts;
        private final List<EventInputIssue> issues;

        private ExtractionResult(MonitorActionFacts facts, List<EventInputIssue> issues) {
            this.facts = facts;
            this.issues = Collections.unmodifiableList(new ArrayList<EventInputIssue>(issues));
        }

        /** @return approved immutable facts extracted from scalar values */
        public MonitorActionFacts getFacts() {
            return facts;
        }

        /** @return immutable stable diagnostics without paths, values or exception text */
        public List<EventInputIssue> getIssues() {
            return issues;
        }
    }

    private static final class Resolution {
        private static final Resolution UNRESOLVED = new Resolution(false, null);

        private final boolean resolved;
        private final Object value;

        private Resolution(boolean resolved, Object value) {
            this.resolved = resolved;
            this.value = value;
        }

        private static Resolution unresolved() {
            return UNRESOLVED;
        }

        private static Resolution resolved(Object value) {
            return new Resolution(true, value);
        }

        private boolean isResolved() {
            return resolved;
        }

        private Object getValue() {
            return value;
        }
    }

    private static final class Segment {
        private final String name;
        private final Integer index;

        private Segment(String name, Integer index) {
            this.name = name;
            this.index = index;
        }

        private static Segment property(String name) {
            return new Segment(name, null);
        }

        private static Segment index(int index) {
            return new Segment(null, Integer.valueOf(index));
        }
    }
}

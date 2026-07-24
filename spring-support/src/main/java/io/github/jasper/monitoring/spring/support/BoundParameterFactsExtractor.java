package io.github.jasper.monitoring.spring.support;

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
import java.util.List;

/** Safely extracts approved facts from annotated method parameters. */
public final class BoundParameterFactsExtractor {
    public MonitorActionFacts extract(Method method, Object[] arguments) {
        MonitorActionFacts.Builder facts = MonitorActionFacts.builder();
        if (method == null || arguments == null) {
            return facts.build();
        }
        java.lang.reflect.Parameter[] parameters = method.getParameters();
        for (int index = 0; index < parameters.length && index < arguments.length; index++) {
            for (MonitorActionAttribute attribute : parameters[index].getAnnotationsByType(MonitorActionAttribute.class)) {
                try {
                    MonitorActionDefinition.validateParameterAttribute(attribute);
                    Object value = value(arguments[index], attribute.path());
                    merge(facts, contribution(attribute, value));
                } catch (RuntimeException ignored) {
                    // Annotation or reflection failures are observational only.
                }
            }
        }
        return facts.build();
    }

    private static MonitorActionFacts contribution(MonitorActionAttribute attribute, Object value) {
        String text = safeScalarText(value);
        if (text == null) {
            return MonitorActionFacts.empty();
        }
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

    private static Object value(Object root, String path) {
        if (root == null) {
            return null;
        }
        List<Segment> segments = parse(path);
        Object value = root;
        for (Segment segment : segments) {
            value = segment.index == null ? property(value, segment.name) : indexed(value, segment.index.intValue());
            if (value == null) {
                return null;
            }
        }
        return value;
    }

    private static List<Segment> parse(String path) {
        if (path == null || path.isEmpty()) {
            return java.util.Collections.emptyList();
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

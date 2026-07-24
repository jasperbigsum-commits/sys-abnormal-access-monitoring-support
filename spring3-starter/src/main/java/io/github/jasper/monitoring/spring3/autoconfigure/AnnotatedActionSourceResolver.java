package io.github.jasper.monitoring.spring3.autoconfigure;

import io.github.jasper.monitoring.api.MonitorAction;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.springframework.aop.support.AopUtils;
import org.springframework.util.ClassUtils;

/** Resolves the concrete handler method and the one source that declares its action. */
final class AnnotatedActionSourceResolver {
    private AnnotatedActionSourceResolver() {
    }

    static ResolvedAction resolve(Method invokedMethod, Class<?> targetType) {
        if (invokedMethod == null) {
            return null;
        }
        Class<?> userType = targetType == null ? null : ClassUtils.getUserClass(targetType);
        Method method = userType == null ? invokedMethod : AopUtils.getMostSpecificMethod(invokedMethod, userType);
        if (method == null) {
            method = invokedMethod;
        }
        Method source = annotatedMethod(method, invokedMethod, userType);
        if (source != null) {
            return new ResolvedAction(method, source, userType);
        }
        Class<?> type = annotatedType(userType, invokedMethod.getDeclaringClass(), method.getDeclaringClass());
        return type == null ? null : new ResolvedAction(method, type, userType);
    }

    private static Method annotatedMethod(Method method, Method invokedMethod, Class<?> targetType) {
        if (hasAction(method)) {
            return method;
        }
        if (!method.equals(invokedMethod) && hasAction(invokedMethod)) {
            return invokedMethod;
        }
        Method source = annotatedInterfaceMethod(targetType, method, targetType, new HashSet<Class<?>>());
        if (source != null) {
            return source;
        }
        return annotatedInterfaceMethod(invokedMethod.getDeclaringClass(), method, targetType,
            new HashSet<Class<?>>());
    }

    private static Method annotatedInterfaceMethod(Class<?> type, Method signature, Class<?> targetType,
                                                   Set<Class<?>> visited) {
        if (type == null || type == Object.class || !visited.add(type)) {
            return null;
        }
        if (type.isInterface()) {
            Method method = matchingMethod(type, signature, targetType);
            if (hasAction(method)) {
                return method;
            }
        }
        for (Class<?> candidate : type.getInterfaces()) {
            Method method = annotatedInterfaceMethod(candidate, signature, targetType, visited);
            if (method != null) {
                return method;
            }
        }
        return annotatedInterfaceMethod(type.getSuperclass(), signature, targetType, visited);
    }

    private static Method matchingMethod(Class<?> type, Method signature, Class<?> targetType) {
        try {
            return type.getMethod(signature.getName(), signature.getParameterTypes());
        } catch (NoSuchMethodException ignored) {
        }
        Method concreteSignature = mostSpecific(signature, targetType);
        for (Method candidate : type.getMethods()) {
            if (!candidate.getName().equals(concreteSignature.getName())
                || candidate.getParameterTypes().length != concreteSignature.getParameterTypes().length) {
                continue;
            }
            if (sameSignature(mostSpecific(candidate, targetType), concreteSignature)) {
                return candidate;
            }
        }
        return null;
    }

    private static Method mostSpecific(Method method, Class<?> targetType) {
        if (targetType == null) {
            return method;
        }
        Method specificMethod = AopUtils.getMostSpecificMethod(method, targetType);
        return specificMethod == null ? method : specificMethod;
    }

    private static boolean sameSignature(Method left, Method right) {
        return left.getName().equals(right.getName())
            && Arrays.equals(left.getParameterTypes(), right.getParameterTypes());
    }

    private static Class<?> annotatedType(Class<?>... types) {
        for (Class<?> type : types) {
            Class<?> source = annotatedClass(type);
            if (source != null) {
                return source;
            }
        }
        Set<Class<?>> visited = new HashSet<Class<?>>();
        for (Class<?> type : types) {
            Class<?> source = annotatedInterface(type, visited);
            if (source != null) {
                return source;
            }
        }
        return null;
    }

    private static Class<?> annotatedClass(Class<?> type) {
        Class<?> current = type;
        while (current != null && current != Object.class) {
            if (current.getAnnotation(MonitorAction.class) != null) {
                return current;
            }
            current = current.getSuperclass();
        }
        return null;
    }

    private static Class<?> annotatedInterface(Class<?> type, Set<Class<?>> visited) {
        if (type == null || type == Object.class) {
            return null;
        }
        for (Class<?> candidate : type.getInterfaces()) {
            Class<?> source = annotatedInterfaceType(candidate, visited);
            if (source != null) {
                return source;
            }
        }
        return annotatedInterface(type.getSuperclass(), visited);
    }

    private static Class<?> annotatedInterfaceType(Class<?> type, Set<Class<?>> visited) {
        if (!visited.add(type)) {
            return null;
        }
        if (type.getAnnotation(MonitorAction.class) != null) {
            return type;
        }
        for (Class<?> candidate : type.getInterfaces()) {
            Class<?> source = annotatedInterfaceType(candidate, visited);
            if (source != null) {
                return source;
            }
        }
        return null;
    }

    private static boolean hasAction(AnnotatedElement element) {
        return element != null && element.getAnnotation(MonitorAction.class) != null;
    }

    static final class ResolvedAction {
        private final Method method;
        private final Method parameterMethod;
        private final AnnotatedElement source;
        private final MonitorAction action;

        private ResolvedAction(Method method, AnnotatedElement source, Class<?> targetType) {
            this.method = method;
            this.parameterMethod = parameterMethod(method, source, targetType);
            this.source = source;
            this.action = source.getAnnotation(MonitorAction.class);
        }

        Method getMethod() {
            return method;
        }

        Method getParameterMethod() {
            return parameterMethod;
        }

        AnnotatedElement getSource() {
            return source;
        }

        MonitorAction getAction() {
            return action;
        }

        private static Method parameterMethod(Method method, AnnotatedElement source, Class<?> targetType) {
            if (source instanceof Method) {
                return (Method) source;
            }
            if (source instanceof Class<?> && ((Class<?>) source).isInterface()) {
                Method interfaceMethod = matchingMethod((Class<?>) source, method, targetType);
                if (interfaceMethod != null) {
                    return interfaceMethod;
                }
            }
            return method;
        }
    }
}

package io.github.jasper.monitoring.core.application.control;

import io.github.jasper.monitoring.core.domain.ControlCommand;


import io.github.jasper.monitoring.core.port.ControlHandler;
import io.github.jasper.monitoring.core.domain.ControlExecution;
import io.github.jasper.monitoring.api.ControlActionType;
import io.github.jasper.monitoring.api.ControlTrigger;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * 基于反射的 {@link ControlHandler}，调用宿主对象中标记 {@link ControlTrigger} 的公开方法。
 *
 * <p>一个目标对象内每种控制动作只能绑定一个方法。被绑定的方法必须恰好接收一个
 * {@link ControlCommand}，并返回 {@code void} 或 {@link ControlExecution}。</p>
 */
public final class AnnotatedControlHandler implements ControlHandler {
    private final Object target;
    private final Map<ControlActionType, Method> bindings;

    /**
     * 在一个宿主对象上创建并校验控制方法绑定。
     *
     * @param target 包含已注解公开控制方法的宿主对象
     * @throws IllegalArgumentException 当绑定方法签名不合法或重复绑定同一动作时
     */
    public AnnotatedControlHandler(Object target) {
        this(target, Objects.requireNonNull(target, "target").getClass());
    }

    private AnnotatedControlHandler(Object target, Class<?> targetType) {
        this.target = Objects.requireNonNull(target, "target");
        this.bindings = resolveInvocationMethods(this.target, bind(targetType));
    }

    /**
     * 创建延迟获取宿主对象的处理器，并立即校验注解元数据。
     *
     * <p>框架适配层可先发现绑定关系，而不必提前创建可能依赖监测服务的 Bean。每次执行控制动作
     * 都会调用一次供应器，因此不会持有请求作用域等短生命周期对象。</p>
     *
     * @param targetType 声明已注解公开控制方法的类型
     * @param targetSupplier 延迟获取宿主对象的供应器
     * @return 在实际创建宿主对象前即可报告支持动作的处理器
     */
    public static ControlHandler lazy(Class<?> targetType, Supplier<?> targetSupplier) {
        return new LazyHandler(targetType, targetSupplier);
    }

    /**
     * 判断对象是否至少包含一个可发现的控制绑定。
     *
     * @param target 待检查的宿主对象
     * @return 存在标记 {@link ControlTrigger} 的公开方法时为 {@code true}
     */
    public static boolean hasBindings(Object target) {
        Objects.requireNonNull(target, "target");
        return hasBindings(target.getClass());
    }

    /**
     * 在不创建实例的情况下，判断类型是否声明了至少一个有效控制绑定。
     *
     * @param targetType 待检查的宿主类型
     * @return 类型中存在一个或多个有效 {@link ControlTrigger} 方法时为 {@code true}
     * @throws IllegalArgumentException 当绑定方法签名不合法或重复绑定同一动作时
     */
    public static boolean hasBindings(Class<?> targetType) {
        return !bind(targetType).isEmpty();
    }

    @Override
    public boolean supports(ControlActionType action) {
        return bindings.containsKey(action);
    }

    /**
     * 调用匹配的宿主方法，并将反射失败转换为不含敏感细节的结果。
     *
     * @param command 待执行的控制动作
     * @return 宿主返回的结果、{@code void} 方法对应的成功结果，或安全的失败结果
     */
    @Override
    public ControlExecution execute(ControlCommand command) {
        Objects.requireNonNull(command, "command");
        Method method = bindings.get(command.getAction());
        if (method == null) {
            return ControlExecution.failed(command.getIdempotencyKey(), "No annotated control method for " + command.getAction());
        }
        try {
            Object value = method.invoke(target, command);
            if (method.getReturnType() == Void.TYPE) {
                return ControlExecution.succeeded(command.getIdempotencyKey());
            }
            return value == null
                ? ControlExecution.failed(command.getIdempotencyKey(), "Annotated control method returned no result")
                : (ControlExecution) value;
        } catch (IllegalAccessException exception) {
            return ControlExecution.failed(command.getIdempotencyKey(), "Annotated control method is not accessible");
        } catch (IllegalArgumentException exception) {
            return ControlExecution.failed(command.getIdempotencyKey(), "Annotated control method could not be invoked");
        } catch (InvocationTargetException exception) {
            return ControlExecution.failed(command.getIdempotencyKey(), "Annotated control method failed");
        }
    }

    private static Map<ControlActionType, Method> bind(Class<?> targetType) {
        Objects.requireNonNull(targetType, "targetType");
        Map<ControlActionType, Method> values = new EnumMap<ControlActionType, Method>(ControlActionType.class);
        for (Method method : targetType.getMethods()) {
            ControlTrigger trigger = method.getAnnotation(ControlTrigger.class);
            if (trigger == null) {
                continue;
            }
            if (trigger.value() == ControlActionType.RECORD) {
                throw new IllegalArgumentException("ControlTrigger cannot bind RECORD");
            }
            validate(method);
            makeAccessible(method);
            if (values.put(trigger.value(), method) != null) {
                throw new IllegalArgumentException("Duplicate ControlTrigger binding for " + trigger.value());
            }
        }
        return values;
    }

    private static Map<ControlActionType, Method> resolveInvocationMethods(Object target,
                                                                             Map<ControlActionType, Method> declared) {
        Map<ControlActionType, Method> values = new EnumMap<ControlActionType, Method>(ControlActionType.class);
        for (Map.Entry<ControlActionType, Method> entry : declared.entrySet()) {
            Method method = entry.getValue();
            try {
                Method runtimeMethod = target.getClass().getMethod(method.getName(), method.getParameterTypes());
                makeAccessible(runtimeMethod);
                values.put(entry.getKey(), runtimeMethod);
            } catch (NoSuchMethodException ignored) {
                values.put(entry.getKey(), method);
            }
        }
        return values;
    }

    private static void validate(Method method) {
        if (!Modifier.isPublic(method.getModifiers()) || method.getParameterTypes().length != 1
            || method.getParameterTypes()[0] != ControlCommand.class
            || (method.getReturnType() != Void.TYPE && method.getReturnType() != ControlExecution.class)) {
            throw new IllegalArgumentException("ControlTrigger method " + method.getName()
                + " must be public, accept one ControlCommand, and return void or ControlExecution");
        }
    }

    private static void makeAccessible(Method method) {
        try {
            if (!method.isAccessible()) {
                method.setAccessible(true);
            }
        } catch (RuntimeException ignored) {
            // Invocation returns a safe failure if a runtime denies reflective access.
        }
    }

    private static final class LazyHandler implements ControlHandler {
        private final Class<?> targetType;
        private final Supplier<?> targetSupplier;
        private final Map<ControlActionType, Method> bindings;

        private LazyHandler(Class<?> targetType, Supplier<?> targetSupplier) {
            this.targetType = Objects.requireNonNull(targetType, "targetType");
            this.targetSupplier = Objects.requireNonNull(targetSupplier, "targetSupplier");
            this.bindings = bind(targetType);
        }

        @Override
        public boolean supports(ControlActionType action) {
            return bindings.containsKey(action);
        }

        @Override
        public ControlExecution execute(ControlCommand command) {
            Objects.requireNonNull(command, "command");
            try {
                Object target = Objects.requireNonNull(targetSupplier.get(), "targetSupplier result");
                return new AnnotatedControlHandler(target, targetType).execute(command);
            } catch (RuntimeException ignored) {
                return ControlExecution.failed(command.getIdempotencyKey(), "Annotated control target unavailable");
            }
        }
    }
}

package io.github.jasper.monitoring.api;

import java.lang.reflect.Method;
import java.util.Objects;

/**
 * Immutable invocation container supplied to a {@link MonitorActionEnricher}.
 *
 * <p>The argument array is copied defensively. Its elements, the return value and the failure remain references to
 * application-owned objects so enrichers can inspect business results; enrichers must not mutate them.</p>
 */
public final class MonitorActionInvocation {
    private final MonitorActionDefinition definition;
    private final Method method;
    private final Object[] arguments;
    private final Phase phase;
    private final Object returnValue;
    private final Throwable failure;
    private final long elapsedMs;

    private MonitorActionInvocation(MonitorActionDefinition definition, Method method, Object[] arguments,
                                    Phase phase, Object returnValue, Throwable failure, long elapsedMs) {
        this.definition = Objects.requireNonNull(definition, "definition");
        this.method = Objects.requireNonNull(method, "method");
        this.arguments = arguments == null ? new Object[0] : arguments.clone();
        this.phase = Objects.requireNonNull(phase, "phase");
        this.returnValue = returnValue;
        this.failure = failure;
        if (elapsedMs < 0) {
            throw new IllegalArgumentException("elapsedMs must not be negative");
        }
        this.elapsedMs = elapsedMs;
    }

    /**
     * Creates a snapshot taken before the action executes.
     *
     * @param definition resolved static action definition
     * @param method intercepted method
     * @param arguments invocation arguments; the array is copied defensively, while its elements are retained by reference
     * @return before-invocation snapshot
     */
    public static MonitorActionInvocation before(MonitorActionDefinition definition, Method method, Object[] arguments) {
        return new MonitorActionInvocation(definition, method, arguments, Phase.BEFORE, null, null, 0L);
    }

    /**
     * Creates a snapshot taken after a normal method return.
     *
     * @param definition resolved static action definition
     * @param method intercepted method
     * @param arguments invocation arguments; the array is copied defensively, while its elements are retained by reference
     * @param returnValue method result, possibly {@code null}
     * @param elapsedMs non-negative elapsed execution time in milliseconds
     * @return post-return snapshot
     */
    public static MonitorActionInvocation returning(MonitorActionDefinition definition, Method method, Object[] arguments,
                                                    Object returnValue, long elapsedMs) {
        return new MonitorActionInvocation(definition, method, arguments, Phase.AFTER_RETURNING, returnValue, null,
            elapsedMs);
    }

    /**
     * Creates a snapshot taken after a method throws.
     *
     * @param definition resolved static action definition
     * @param method intercepted method
     * @param arguments invocation arguments; the array is copied defensively, while its elements are retained by reference
     * @param failure observed method failure
     * @param elapsedMs non-negative elapsed execution time in milliseconds
     * @return post-failure snapshot
     */
    public static MonitorActionInvocation throwing(MonitorActionDefinition definition, Method method, Object[] arguments,
                                                   Throwable failure, long elapsedMs) {
        return new MonitorActionInvocation(definition, method, arguments, Phase.AFTER_THROWING, null,
            Objects.requireNonNull(failure, "failure"), elapsedMs);
    }

    /** @return resolved immutable action definition */
    public MonitorActionDefinition getDefinition() {
        return definition;
    }

    /** @return intercepted method */
    public Method getMethod() {
        return method;
    }

    /** @return a defensive copy of the invocation arguments */
    public Object[] getArguments() {
        return arguments.clone();
    }

    /** @return invocation collection phase */
    public Phase getPhase() {
        return phase;
    }

    /** @return application-owned method return value, or {@code null} before invocation or after a failure */
    public Object getReturnValue() {
        return returnValue;
    }

    /** @return method failure, or {@code null} before invocation and after a normal return */
    public Throwable getFailure() {
        return failure;
    }

    /** @return non-negative elapsed execution time in milliseconds */
    public long getElapsedMs() {
        return elapsedMs;
    }

    /** Invocation points at which a host enricher can run. */
    public enum Phase {
        BEFORE,
        AFTER_RETURNING,
        AFTER_THROWING
    }
}

package io.github.jasper.monitoring.spring3.autoconfigure;

import io.github.jasper.monitoring.api.MonitoringContextAccessor;
import io.github.jasper.monitoring.api.action.MonitorAction;
import io.github.jasper.monitoring.api.event.ActionExecution;
import io.github.jasper.monitoring.api.event.ActionOutcome;
import io.github.jasper.monitoring.core.application.MonitoringService;
import java.lang.reflect.Method;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.aop.support.StaticMethodMatcherPointcutAdvisor;

/** AOP entry point for the strict typed action annotation. */
public final class TypedMonitorActionAspect extends StaticMethodMatcherPointcutAdvisor
        implements MethodInterceptor {
    private static final long serialVersionUID = 1L;
    private final MonitoringService monitoring;
    private final MonitoringContextAccessor context;

    public TypedMonitorActionAspect(MonitoringService monitoring, MonitoringContextAccessor context) {
        this.monitoring = Objects.requireNonNull(monitoring, "monitoring");
        this.context = Objects.requireNonNull(context, "context");
        setAdvice(this);
    }

    @Override public boolean matches(Method method, Class<?> targetClass) {
        return method.isAnnotationPresent(MonitorAction.class);
    }

    @Override public Object invoke(MethodInvocation invocation) throws Throwable {
        MonitorAction annotation = invocation.getMethod().getAnnotation(MonitorAction.class);
        long startedAt = System.nanoTime();
        Object result;
        try {
            result = invocation.proceed();
        } catch (Throwable failure) {
            try {
                monitor(annotation, ActionOutcome.failure("ACTION_INVOCATION_FAILED",
                    ActionOutcome.ExceptionClassification.UNKNOWN, elapsed(startedAt)));
            } catch (RuntimeException monitoringFailure) {
                failure.addSuppressed(monitoringFailure);
            }
            throw failure;
        }
        monitor(annotation, ActionOutcome.success(elapsed(startedAt)));
        return result;
    }

    private void monitor(MonitorAction annotation, ActionOutcome outcome) {
        monitoring.monitor(ActionExecution.of(annotation.value(), context.requestContext(),
            context.identityContext(), outcome));
    }

    private static long elapsed(long startedAt) {
        return Math.max(0L, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt));
    }
}

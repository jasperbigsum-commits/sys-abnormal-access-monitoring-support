package io.github.jasper.monitoring.spring2.autoconfigure;

import io.github.jasper.monitoring.api.MonitoringContextAccessor;
import io.github.jasper.monitoring.api.action.MonitorAction;
import io.github.jasper.monitoring.api.event.ActionExecution;
import io.github.jasper.monitoring.api.event.ActionOutcome;
import io.github.jasper.monitoring.api.fact.ActionFacts;
import io.github.jasper.monitoring.api.fact.FactSource;
import io.github.jasper.monitoring.core.application.MonitoringService;
import io.github.jasper.monitoring.spring.support.ActionFactExtractor;
import io.github.jasper.monitoring.spring.support.MonitorActionContractValidator;
import java.lang.reflect.Method;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.aop.support.StaticMethodMatcherPointcutAdvisor;
import org.springframework.http.ResponseEntity;

/** AOP entry point for the strict typed action annotation. */
public final class TypedMonitorActionAspect extends StaticMethodMatcherPointcutAdvisor
        implements MethodInterceptor {
    private static final long serialVersionUID = 1L;
    private final MonitoringService monitoring;
    private final MonitoringContextAccessor context;
    private final ActionFactExtractor facts;
    private final MonitorActionContractValidator contracts;

    public TypedMonitorActionAspect(MonitoringService monitoring, MonitoringContextAccessor context,
            ActionFactExtractor facts, MonitorActionContractValidator contracts) {
        this.monitoring = Objects.requireNonNull(monitoring, "monitoring");
        this.context = Objects.requireNonNull(context, "context");
        this.facts = Objects.requireNonNull(facts, "facts");
        this.contracts = Objects.requireNonNull(contracts, "contracts");
        setAdvice(this);
    }

    @Override public boolean matches(Method method, Class<?> targetClass) {
        if (!method.isAnnotationPresent(MonitorAction.class)) return false;
        contracts.validate(method);
        return true;
    }

    @Override public Object invoke(MethodInvocation invocation) throws Throwable {
        MonitorActionContractValidator.MethodBinding binding = contracts.validate(invocation.getMethod());
        ActionFacts suppliedFacts = facts.extract(binding, invocation.getArguments());
        long startedAt = System.nanoTime();
        Object result;
        try {
            result = invocation.proceed();
        } catch (Throwable failure) {
            try {
                monitor(binding, suppliedFacts, ActionOutcome.failure("ACTION_INVOCATION_FAILED",
                    ActionOutcome.ExceptionClassification.UNKNOWN, elapsed(startedAt)));
            } catch (RuntimeException monitoringFailure) {
                failure.addSuppressed(monitoringFailure);
            }
            throw failure;
        }
        monitor(binding, suppliedFacts, outcome(result, elapsed(startedAt)));
        return result;
    }

    private static ActionOutcome outcome(Object value, long elapsed) {
        if (value instanceof ResponseEntity) {
            int status = ((ResponseEntity<?>) value).getStatusCode().value();
            if (status == 401 || status == 403) {
                return ActionOutcome.denied("HTTP_ACCESS_DENIED", elapsed);
            }
            if (status >= 400) {
                return ActionOutcome.failure("HTTP_REQUEST_FAILED",
                    ActionOutcome.ExceptionClassification.UNKNOWN, elapsed);
            }
        }
        return ActionOutcome.success(elapsed);
    }

    private void monitor(MonitorActionContractValidator.MethodBinding binding,
            ActionFacts suppliedFacts, ActionOutcome outcome) {
        monitoring.monitor(ActionExecution.of(binding.getActionType(), context.requestContext(),
            context.identityContext(), outcome, suppliedFacts, FactSource.METHOD_PARAMETER));
    }

    private static long elapsed(long startedAt) {
        return Math.max(0L, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt));
    }
}

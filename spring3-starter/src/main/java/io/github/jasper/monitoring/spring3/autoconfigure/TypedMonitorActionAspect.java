package io.github.jasper.monitoring.spring3.autoconfigure;

import io.github.jasper.monitoring.api.MonitoringContextAccessor;
import io.github.jasper.monitoring.api.action.MonitorAction;
import io.github.jasper.monitoring.api.action.ActionDecision;
import io.github.jasper.monitoring.api.action.ActionFailurePolicy;
import io.github.jasper.monitoring.api.code.BuiltInReasonCodes;
import io.github.jasper.monitoring.api.event.ActionExecution;
import io.github.jasper.monitoring.api.event.ActionOutcome;
import io.github.jasper.monitoring.api.event.FailureClass;
import io.github.jasper.monitoring.api.error.ActionBlockedException;
import io.github.jasper.monitoring.api.fact.ActionFacts;
import io.github.jasper.monitoring.api.fact.FactSource;
import io.github.jasper.monitoring.api.fact.FactType;
import io.github.jasper.monitoring.core.application.MonitoringService;
import io.github.jasper.monitoring.spring.support.ActionFactExtractor;
import io.github.jasper.monitoring.spring.support.MonitorActionContractValidator;
import io.github.jasper.monitoring.spring.support.MonitoringFactScope;
import io.github.jasper.monitoring.spring.support.MonitoringCheckpoint;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;
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
        MonitoringFactScope scope = MonitoringFactScope.open(binding.getActionType(), new MonitoringCheckpoint() {
            @Override public ActionDecision decide(ActionFacts runtimeFacts) {
                try {
                    MergedFacts merged = mergedFacts(suppliedFacts, runtimeFacts);
                    return monitoring.decide(ActionExecution.of(binding.getActionType(),
                        context.requestContext(), context.identityContext(), ActionOutcome.success(0L),
                        merged.facts, merged.sources));
                } catch (RuntimeException failure) {
                    if (binding.getAction().getFailurePolicy() == ActionFailurePolicy.FAIL_CLOSED) {
                        throw new ActionBlockedException(ActionDecision.blocked("MONITORING_DECISION_FAILED"));
                    }
                    return ActionDecision.allow();
                }
            }
        });
        try {
            Object result;
            try {
                result = invocation.proceed();
            } catch (ActionBlockedException blocked) {
                scope.complete(ActionOutcome.denied(BuiltInReasonCodes.Action.BLOCKED, elapsed(startedAt)));
                try {
                    monitor(binding, suppliedFacts, scope.snapshot(),
                        ActionOutcome.denied(BuiltInReasonCodes.Action.BLOCKED, elapsed(startedAt)));
                } catch (RuntimeException monitoringFailure) {
                    blocked.addSuppressed(monitoringFailure);
                }
                throw blocked;
            } catch (Throwable failure) {
                scope.complete(ActionOutcome.failure(BuiltInReasonCodes.Action.INVOCATION_FAILED,
                    FailureClass.UNKNOWN, elapsed(startedAt)));
                try {
                    monitor(binding, suppliedFacts, scope.snapshot(),
                        ActionOutcome.failure(BuiltInReasonCodes.Action.INVOCATION_FAILED,
                            FailureClass.UNKNOWN, elapsed(startedAt)));
                } catch (RuntimeException monitoringFailure) {
                    failure.addSuppressed(monitoringFailure);
                }
                throw failure;
            }
            ActionOutcome completed = outcome(result, elapsed(startedAt));
            scope.complete(completed);
            monitor(binding, suppliedFacts, scope.snapshot(), completed);
            return result;
        } finally {
            scope.close();
        }
    }

    private static ActionOutcome outcome(Object value, long elapsed) {
        if (value instanceof ResponseEntity) {
            int status = ((ResponseEntity<?>) value).getStatusCode().value();
            if (status == 401 || status == 403) {
                return ActionOutcome.denied(BuiltInReasonCodes.Action.BLOCKED, elapsed);
            }
            if (status >= 400) {
                return ActionOutcome.failure(BuiltInReasonCodes.Action.REQUEST_FAILED,
                    FailureClass.UNKNOWN, elapsed);
            }
        }
        return ActionOutcome.success(elapsed);
    }

    private void monitor(MonitorActionContractValidator.MethodBinding binding,
            ActionFacts parameterFacts, ActionFacts runtimeFacts, ActionOutcome outcome) {
        MergedFacts merged = mergedFacts(parameterFacts, runtimeFacts);
        monitoring.monitor(ActionExecution.of(binding.getActionType(), context.requestContext(),
            context.identityContext(), outcome, merged.facts, merged.sources));
    }

    private static MergedFacts mergedFacts(ActionFacts parameterFacts, ActionFacts runtimeFacts) {
        ActionFacts.Builder merged = ActionFacts.builder();
        Map<Class<? extends FactType<?>>, FactSource> sources =
            new LinkedHashMap<Class<? extends FactType<?>>, FactSource>();
        addFacts(merged, sources, parameterFacts, FactSource.METHOD_PARAMETER);
        addFacts(merged, sources, runtimeFacts, FactSource.HOST_PROVIDER);
        return new MergedFacts(merged.build(), sources);
    }

    private static final class MergedFacts {
        private final ActionFacts facts;
        private final Map<Class<? extends FactType<?>>, FactSource> sources;
        private MergedFacts(ActionFacts facts, Map<Class<? extends FactType<?>>, FactSource> sources) {
            this.facts = facts;
            this.sources = sources;
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void addFacts(ActionFacts.Builder merged,
            Map<Class<? extends FactType<?>>, FactSource> sources,
            ActionFacts facts, FactSource source) {
        for (Map.Entry<Class<? extends FactType<?>>, Object> entry : facts.asMap().entrySet()) {
            if (sources.containsKey(entry.getKey())) {
                throw new IllegalStateException("Multiple supplied sources returned same fact: "
                    + entry.getKey().getName());
            }
            merged.put((Class) entry.getKey(), entry.getValue());
            sources.put(entry.getKey(), source);
        }
    }

    private static long elapsed(long startedAt) {
        return Math.max(0L, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt));
    }
}

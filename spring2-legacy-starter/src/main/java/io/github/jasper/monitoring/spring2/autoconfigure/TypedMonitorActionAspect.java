package io.github.jasper.monitoring.spring2.autoconfigure;

import io.github.jasper.monitoring.api.MonitoringContextAccessor;
import io.github.jasper.monitoring.api.action.MonitorAction;
import io.github.jasper.monitoring.api.action.ResourceAccess;
import io.github.jasper.monitoring.api.event.ActionExecution;
import io.github.jasper.monitoring.api.event.ActionOutcome;
import io.github.jasper.monitoring.api.event.FailureClass;
import io.github.jasper.monitoring.api.code.BuiltInReasonCodes;
import io.github.jasper.monitoring.api.fact.ActionFacts;
import io.github.jasper.monitoring.api.fact.FactSource;
import io.github.jasper.monitoring.api.fact.FactType;
import io.github.jasper.monitoring.core.application.MonitoringService;
import io.github.jasper.monitoring.spring.support.ActionFactExtractor;
import io.github.jasper.monitoring.spring.support.MonitorActionContractValidator;
import io.github.jasper.monitoring.spring.support.MonitoringFactScope;
import io.github.jasper.monitoring.spring.support.ResourceAccessStage;
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
    private final ResourceAccessStage resourceAccess;

    public TypedMonitorActionAspect(MonitoringService monitoring, MonitoringContextAccessor context,
            ActionFactExtractor facts, MonitorActionContractValidator contracts, ResourceAccessStage resourceAccess) {
        this.monitoring = Objects.requireNonNull(monitoring, "monitoring");
        this.context = Objects.requireNonNull(context, "context");
        this.facts = Objects.requireNonNull(facts, "facts");
        this.contracts = Objects.requireNonNull(contracts, "contracts");
        this.resourceAccess = Objects.requireNonNull(resourceAccess, "resourceAccess");
        setAdvice(this);
    }

    @Override public boolean matches(Method method, Class<?> targetClass) {
        if (method.isAnnotationPresent(ResourceAccess.class)
                && !method.isAnnotationPresent(MonitorAction.class)) {
            contracts.validate(method);
        }
        if (!method.isAnnotationPresent(MonitorAction.class)) return false;
        contracts.validate(method);
        return true;
    }

    @Override public Object invoke(MethodInvocation invocation) throws Throwable {
        MonitorActionContractValidator.MethodBinding binding = contracts.validate(invocation.getMethod());
        ActionFacts suppliedFacts = facts.extract(binding, invocation.getArguments());
        final ActionFacts resourceFacts;
        if (binding.hasResourceAccess()) {
            resourceFacts = resourceAccess.authorize(binding,
                initialFacts(binding.getStaticFacts(), suppliedFacts));
        } else resourceFacts = ActionFacts.builder().build();
        long startedAt = System.nanoTime();
        MonitoringFactScope scope = MonitoringFactScope.open();
        try {
            Object result;
            try {
                result = invocation.proceed();
            } catch (Throwable failure) {
                try {
                    monitor(binding, suppliedFacts, resourceFacts, scope.snapshot(),
                        ActionOutcome.failure(BuiltInReasonCodes.Action.INVOCATION_FAILED,
                            FailureClass.UNKNOWN, elapsed(startedAt)));
                } catch (RuntimeException monitoringFailure) {
                    failure.addSuppressed(monitoringFailure);
                }
                throw failure;
            }
            monitor(binding, suppliedFacts, resourceFacts, scope.snapshot(), outcome(result, elapsed(startedAt)));
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
            ActionFacts parameterFacts, ActionFacts resourceFacts,
            ActionFacts runtimeFacts, ActionOutcome outcome) {
        ActionFacts.Builder merged = ActionFacts.builder();
        Map<Class<? extends FactType<?>>, FactSource> sources =
            new LinkedHashMap<Class<? extends FactType<?>>, FactSource>();
        addFacts(merged, sources, binding.getStaticFacts(), FactSource.HOST_PROVIDER);
        addFacts(merged, sources, parameterFacts, FactSource.METHOD_PARAMETER);
        addFacts(merged, sources, resourceFacts, FactSource.HOST_PROVIDER);
        addFacts(merged, sources, runtimeFacts, FactSource.HOST_PROVIDER);
        ActionFacts validated = facts.validate(binding, merged.build(), sources);
        monitoring.monitor(ActionExecution.of(binding.getActionType(), context.requestContext(),
            context.identityContext(), outcome, validated, sources));
    }

    private static ActionFacts initialFacts(ActionFacts staticFacts, ActionFacts parameterFacts) {
        ActionFacts.Builder merged = ActionFacts.builder();
        Map<Class<? extends FactType<?>>, FactSource> sources =
            new LinkedHashMap<Class<? extends FactType<?>>, FactSource>();
        addFacts(merged, sources, staticFacts, FactSource.HOST_PROVIDER);
        addFacts(merged, sources, parameterFacts, FactSource.METHOD_PARAMETER);
        return merged.build();
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

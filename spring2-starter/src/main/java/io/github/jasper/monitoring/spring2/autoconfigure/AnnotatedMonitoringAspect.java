package io.github.jasper.monitoring.spring2.autoconfigure;

import io.github.jasper.monitoring.api.MonitorActionEnricher;
import io.github.jasper.monitoring.api.MonitorActionFacts;
import io.github.jasper.monitoring.api.MonitorActionInvocation;
import io.github.jasper.monitoring.spring.support.AnnotatedActionFacts;
import io.github.jasper.monitoring.spring.support.BoundParameterFactsExtractor;
import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.aop.support.StaticMethodMatcherPointcutAdvisor;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

/** Collects dynamic action facts without changing the controller invocation. */
public final class AnnotatedMonitoringAspect extends StaticMethodMatcherPointcutAdvisor
    implements MethodInterceptor {
    private static final long serialVersionUID = 1L;

    private final ListableBeanFactory beanFactory;
    private final BoundParameterFactsExtractor parameters = new BoundParameterFactsExtractor();

    public AnnotatedMonitoringAspect(ListableBeanFactory beanFactory) {
        this.beanFactory = beanFactory;
        setAdvice(this);
    }

    @Override
    public boolean matches(Method method, Class<?> targetClass) {
        return AnnotatedActionSourceResolver.resolve(method, targetClass) != null;
    }

    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
        AnnotatedActionFacts facts = currentFacts();
        if (facts == null) {
            return invocation.proceed();
        }
        Object target = invocation.getThis();
        AnnotatedActionSourceResolver.ResolvedAction action = AnnotatedActionSourceResolver.resolve(
            invocation.getMethod(), target == null ? null : target.getClass());
        if (action == null || !sameMethod(facts.getMethod(), action.getMethod())) {
            return invocation.proceed();
        }
        Method method = action.getMethod();
        Object[] arguments = invocation.getArguments();
        facts.merge(parameters.extract(action.getParameterMethod(), arguments));
        enrich(facts, MonitorActionInvocation.before(facts.getDefinition(), method, arguments));
        long startedAt = System.nanoTime();
        try {
            Object result = invocation.proceed();
            enrich(facts, MonitorActionInvocation.returning(facts.getDefinition(), method, arguments, result,
                elapsedMillis(startedAt)));
            return result;
        } catch (Throwable failure) {
            enrich(facts, MonitorActionInvocation.throwing(facts.getDefinition(), method, arguments, failure,
                elapsedMillis(startedAt)));
            throw failure;
        }
    }

    private void enrich(AnnotatedActionFacts facts, MonitorActionInvocation invocation) {
        for (Class<? extends MonitorActionEnricher> type : facts.getEnrichers()) {
            try {
                MonitorActionEnricher enricher = beanFactory.getBean(type);
                MonitorActionFacts contribution = enricher.enrich(invocation);
                facts.merge(contribution);
            } catch (RuntimeException ignored) {
                // Host enrichers are observational and may not affect the controller call.
            }
        }
    }

    private static AnnotatedActionFacts currentFacts() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return null;
        }
        Object value = attributes.getAttribute(AnnotatedActionFacts.REQUEST_ATTRIBUTE, RequestAttributes.SCOPE_REQUEST);
        return value instanceof AnnotatedActionFacts ? (AnnotatedActionFacts) value : null;
    }

    private static boolean sameMethod(Method expected, Method actual) {
        if (expected.equals(actual)) {
            return true;
        }
        Class<?> expectedType = expected.getDeclaringClass();
        Class<?> actualType = actual.getDeclaringClass();
        return expected.getName().equals(actual.getName())
            && java.util.Arrays.equals(expected.getParameterTypes(), actual.getParameterTypes())
            && (expectedType.isAssignableFrom(actualType) || actualType.isAssignableFrom(expectedType));
    }

    private static long elapsedMillis(long startedAt) {
        return Math.max(0L, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt));
    }
}

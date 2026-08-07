package io.github.jasper.monitoring.spring.support;

import io.github.jasper.monitoring.api.action.ActionCatalog;
import io.github.jasper.monitoring.api.action.ActionDefinition;
import io.github.jasper.monitoring.api.action.ActionType;
import io.github.jasper.monitoring.api.action.MonitorAction;
import io.github.jasper.monitoring.api.action.ResourceAccess;
import io.github.jasper.monitoring.api.fact.BuiltInFacts;
import io.github.jasper.monitoring.api.error.MonitoringConfigurationException;
import io.github.jasper.monitoring.api.error.MonitoringErrorCode;
import io.github.jasper.monitoring.api.fact.ActionFact;
import io.github.jasper.monitoring.api.fact.ActionFacts;
import io.github.jasper.monitoring.api.fact.FactBinding;
import io.github.jasper.monitoring.api.fact.FactCatalog;
import io.github.jasper.monitoring.api.fact.FactDefinition;
import io.github.jasper.monitoring.api.fact.FactSource;
import io.github.jasper.monitoring.api.fact.FactType;
import io.github.jasper.monitoring.api.fact.StaticActionFact;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Compiles and validates immutable method-parameter fact bindings before invocation. */
public final class MonitorActionContractValidator {
    private final ActionCatalog actions;
    private final FactCatalog facts;
    private final List<FactBinding> providers;
    private final ConcurrentMap<Method, MethodBinding> bindings =
        new ConcurrentHashMap<Method, MethodBinding>();

    public MonitorActionContractValidator(ActionCatalog actions, FactCatalog facts,
            List<FactBinding> providers) {
        this.actions = Objects.requireNonNull(actions, "actions");
        this.facts = Objects.requireNonNull(facts, "facts");
        if (!actions.isFrozen() || !facts.isFrozen()) {
            throw new IllegalArgumentException("Action and fact catalogs must be frozen");
        }
        this.providers = Collections.unmodifiableList(new ArrayList<FactBinding>(
            Objects.requireNonNull(providers, "providers")));
    }

    /** Validates a monitored method and returns its cached immutable binding. */
    public MethodBinding validate(Method method) {
        Objects.requireNonNull(method, "method");
        MethodBinding existing = bindings.get(method);
        if (existing != null) return existing;
        MethodBinding compiled = compile(method);
        MethodBinding raced = bindings.putIfAbsent(method, compiled);
        return raced == null ? compiled : raced;
    }

    private MethodBinding compile(Method method) {
        MonitorAction monitored = method.getAnnotation(MonitorAction.class);
        if (monitored == null) throw configuration("Method is not annotated with MonitorAction");
        Class<? extends ActionType> actionType = monitored.value();
        ActionDefinition action = actions.require(actionType);
        boolean resourceAccess = method.isAnnotationPresent(ResourceAccess.class);
        if (resourceAccess && !action.getRequiredFacts().contains(BuiltInFacts.ResourceId.class)
                && !action.getOptionalFacts().contains(BuiltInFacts.ResourceId.class)) {
            throw configuration("ResourceAccess requires ResourceId to be declared by the monitored action");
        }
        ResourceAccess access = method.getAnnotation(ResourceAccess.class);
        if (access != null && access.requireOrgScope()
                && ((!action.getOptionalFacts().contains(BuiltInFacts.OrgScope.class)
                    && !action.getRequiredFacts().contains(BuiltInFacts.OrgScope.class))
                    || !action.getAllowedSources(BuiltInFacts.OrgScope.class)
                        .contains(FactSource.HOST_PROVIDER))) {
            throw configuration("ResourceAccess requiring OrgScope must declare OrgScope from HOST_PROVIDER");
        }
        List<ParameterFact> result = new ArrayList<ParameterFact>();
        Set<Class<? extends FactType<?>>> ownership =
            new LinkedHashSet<Class<? extends FactType<?>>>();
        ActionFacts.Builder staticFacts = ActionFacts.builder();
        for (StaticActionFact staticFact : method.getAnnotationsByType(StaticActionFact.class)) {
            Class<? extends FactType<?>> factType = staticFact.fact();
            FactDefinition<?> definition = facts.require(factType);
            if (!action.getRequiredFacts().contains(factType)
                    && !action.getOptionalFacts().contains(factType)) {
                throw configuration("StaticActionFact is not declared by the monitored action");
            }
            if (!action.getAllowedSources(factType).contains(FactSource.HOST_PROVIDER)
                    || !definition.allows(FactSource.HOST_PROVIDER)) {
                throw configuration("StaticActionFact does not allow HOST_PROVIDER source");
            }
            if (!ownership.add(factType)) {
                throw configuration("Multiple declarations produce the same action fact");
            }
            rejectProviderConflict(actionType, factType);
            try {
                putRaw(staticFacts, factType, definition.decode(staticFact.value()));
            } catch (RuntimeException invalid) {
                throw configuration("StaticActionFact value failed validation");
            }
        }
        Annotation[][] annotations = method.getParameterAnnotations();
        for (int index = 0; index < annotations.length; index++) {
            for (Annotation annotation : annotations[index]) {
                if (!(annotation instanceof ActionFact)) continue;
                ActionFact fact = (ActionFact) annotation;
                Class<? extends FactType<?>> factType = fact.value();
                FactDefinition<?> definition = facts.require(factType);
                if (!action.getRequiredFacts().contains(factType)
                        && !action.getOptionalFacts().contains(factType)) {
                    throw configuration("ActionFact is not declared by the monitored action");
                }
                if (!action.getAllowedSources(factType).contains(FactSource.METHOD_PARAMETER)
                        || !definition.allows(FactSource.METHOD_PARAMETER)) {
                    throw configuration("ActionFact does not allow METHOD_PARAMETER source");
                }
                if (!ownership.add(factType)) {
                    throw configuration("Multiple declarations produce the same action fact");
                }
                rejectProviderConflict(actionType, factType);
                ActionFactExtractor.validatePath(fact.path());
                result.add(new ParameterFact(index, factType, fact.path()));
            }
        }
        return new MethodBinding(method, actionType, action, result, staticFacts.build(), resourceAccess);
    }

    private void rejectProviderConflict(Class<? extends ActionType> actionType,
            Class<? extends FactType<?>> factType) {
        for (FactBinding provider : providers) {
            if (provider.appliesTo(actionType) && provider.getDeclaredFacts().contains(factType)) {
                throw configuration("ActionFact conflicts with an existing FactBinding producer");
            }
        }
    }

    private static MonitoringConfigurationException configuration(String message) {
        return new MonitoringConfigurationException(
            MonitoringErrorCode.CONFLICTING_ACTION_DEFINITION, message);
    }

    /** Immutable action and parameter fact metadata consumed by instrumentation adapters. */
    public static final class MethodBinding {
        private final Class<? extends ActionType> actionType;
        private final Method method;
        private final ActionDefinition action;
        private final List<ParameterFact> facts;
        private final ActionFacts staticFacts;
        private final boolean resourceAccess;

        private MethodBinding(Method method, Class<? extends ActionType> actionType, ActionDefinition action,
                List<ParameterFact> facts, ActionFacts staticFacts, boolean resourceAccess) {
            this.method = method;
            this.actionType = actionType;
            this.action = action;
            this.facts = Collections.unmodifiableList(new ArrayList<ParameterFact>(facts));
            this.staticFacts = staticFacts;
            this.resourceAccess = resourceAccess;
        }

        public Class<? extends ActionType> getActionType() { return actionType; }
        public ActionDefinition getAction() { return action; }
        public List<ParameterFact> getFacts() { return facts; }
        public ActionFacts getStaticFacts() { return staticFacts; }
        public boolean hasResourceAccess() { return resourceAccess; }
        public Method getMethod() { return method; }
    }

    /** One fact owned by one zero-based method parameter. */
    public static final class ParameterFact {
        private final int parameterIndex;
        private final Class<? extends FactType<?>> factType;
        private final String path;

        private ParameterFact(int parameterIndex, Class<? extends FactType<?>> factType, String path) {
            this.parameterIndex = parameterIndex;
            this.factType = factType;
            this.path = path;
        }

        public int getParameterIndex() { return parameterIndex; }
        public Class<? extends FactType<?>> getFactType() { return factType; }
        public String getPath() { return path; }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void putRaw(ActionFacts.Builder builder,
            Class<? extends FactType<?>> factType, Object value) {
        builder.put((Class) factType, value);
    }
}

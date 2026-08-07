package io.github.jasper.monitoring.spring.support;

import io.github.jasper.monitoring.api.AuthorizationDecision;
import io.github.jasper.monitoring.api.IdentityContext;
import io.github.jasper.monitoring.api.MonitoringContextAccessor;
import io.github.jasper.monitoring.api.MonitoringRequestContext;
import io.github.jasper.monitoring.api.ResourceScopeRequest;
import io.github.jasper.monitoring.api.ResourceScopeResolveRequest;
import io.github.jasper.monitoring.api.ResourceScopeResolution;
import io.github.jasper.monitoring.api.ResourceScopeResolver;
import io.github.jasper.monitoring.api.action.ActionDecision;
import io.github.jasper.monitoring.api.action.ResourceAccess;
import io.github.jasper.monitoring.api.error.ActionBlockedException;
import io.github.jasper.monitoring.api.fact.ActionFacts;
import io.github.jasper.monitoring.api.fact.BuiltInFacts;
import io.github.jasper.monitoring.api.fact.FactType;
import io.github.jasper.monitoring.core.application.authorization.ResourceAccessGuard;
import java.util.Map;
import java.util.Objects;

/** Resource authorization phase shared by Boot 2 and Boot 3 instrumentation. */
public final class ResourceAccessStage {
    private final ResourceAccessGuard guard;
    private final MonitoringContextAccessor context;
    private final ResourceScopeResolver resolver;
    private final ActionFactExtractor factExtractor;

    public ResourceAccessStage(ResourceAccessGuard guard, MonitoringContextAccessor context,
            ResourceScopeResolver resolver, ActionFactExtractor factExtractor) {
        this.guard = Objects.requireNonNull(guard, "guard");
        this.context = Objects.requireNonNull(context, "context");
        this.resolver = Objects.requireNonNull(resolver, "resolver");
        this.factExtractor = Objects.requireNonNull(factExtractor, "factExtractor");
    }

    public ActionFacts authorize(MonitorActionContractValidator.MethodBinding binding, ActionFacts facts) {
        if (!binding.hasResourceAccess()) return ActionFacts.builder().build();
        MonitoringRequestContext request = context.requestContext();
        IdentityContext identity = context.identityContext();
        String resourceId = facts.get(BuiltInFacts.ResourceId.class);
        String orgScope = facts.get(BuiltInFacts.OrgScope.class);
        ResourceAccess access = binding.getMethod().getAnnotation(ResourceAccess.class);
        ActionFacts resolvedFacts = ActionFacts.builder().build();
        boolean resolutionFailed = false;
        if (orgScope == null) {
            ResourceScopeResolution resolution;
            try {
                resolution = resolver.resolve(new ResourceScopeResolveRequest(request, identity,
                    binding.getActionType(), binding.getAction().getCode(),
                    binding.getAction().getResourceType(), resourceId, facts));
                if (resolution == null) throw new IllegalStateException("ResourceScopeResolver returned null");
            } catch (RuntimeException failure) {
                resolution = ResourceScopeResolution.unresolved();
                resolutionFailed = true;
            }
            resolvedFacts = resolution.getFacts();
            rejectDuplicates(facts, resolvedFacts);
            resolvedFacts = factExtractor.validateSupplied(binding, resolvedFacts,
                io.github.jasper.monitoring.api.fact.FactSource.HOST_PROVIDER);
            orgScope = resolvedFacts.get(BuiltInFacts.OrgScope.class);
        }
        ActionFacts authorizationFacts = merge(facts, resolvedFacts);
        String passSubject = access.passSubject();
        if (passSubject.trim().isEmpty()) {
            String userId = identity.getUserId();
            passSubject = userId == null ? null : "user:" + userId;
        }
        AuthorizationDecision decision = guard.authorize(
            identity, new ResourceScopeRequest(request,
                binding.getAction().getResourceType(), resourceId, orgScope,
                emptyToNull(access.passRuleId()), passSubject,
                access.requireOrgScope(), resolutionFailed, authorizationFacts));
        if (!decision.isAllowed()) {
            throw new ActionBlockedException(ActionDecision.blocked(
                decision.getReason() == null ? "RESOURCE_ACCESS_DENIED" : decision.getReason().getCode()));
        }
        return resolvedFacts;
    }

    private static void rejectDuplicates(ActionFacts existing, ActionFacts resolved) {
        for (Map.Entry<Class<? extends FactType<?>>, Object> entry : resolved.asMap().entrySet()) {
            if (existing.asMap().containsKey(entry.getKey())) {
                throw new IllegalStateException("Resource scope resolver returned existing fact: "
                    + entry.getKey().getName());
            }
        }
    }

    private static ActionFacts merge(ActionFacts existing, ActionFacts resolved) {
        ActionFacts.Builder builder = ActionFacts.builder();
        addAll(builder, existing);
        addAll(builder, resolved);
        return builder.build();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void addAll(ActionFacts.Builder builder, ActionFacts facts) {
        for (Map.Entry<Class<? extends FactType<?>>, Object> entry : facts.asMap().entrySet()) {
            builder.put((Class) entry.getKey(), entry.getValue());
        }
    }

    private static String emptyToNull(String value) {
        return value == null || value.trim().isEmpty() ? null : value;
    }
}

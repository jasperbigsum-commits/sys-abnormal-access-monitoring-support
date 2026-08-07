package io.github.jasper.monitoring.api;

/** Resolves trusted server-side ownership and other typed facts for a resource access. */
public interface ResourceScopeResolver {
    /**
     * Resolves resource facts without trusting client-supplied ownership attributes.
     *
     * @param request trusted resolution context
     * @return a non-null resolution; unresolved resources should return
     *         {@link ResourceScopeResolution#unresolved()}
     */
    ResourceScopeResolution resolve(ResourceScopeResolveRequest request);
}

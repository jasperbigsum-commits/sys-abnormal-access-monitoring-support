package io.github.jasper.monitoring.api;

import io.github.jasper.monitoring.api.fact.ActionFacts;
import java.util.Objects;

/** Immutable typed facts resolved from a trusted server-side resource source. */
public final class ResourceScopeResolution {
    private static final ResourceScopeResolution UNRESOLVED = new ResourceScopeResolution(
        false, ActionFacts.builder().build());

    private final boolean resolved;
    private final ActionFacts facts;

    private ResourceScopeResolution(boolean resolved, ActionFacts facts) {
        this.resolved = resolved;
        this.facts = facts;
    }

    public static ResourceScopeResolution unresolved() {
        return UNRESOLVED;
    }

    public static ResourceScopeResolution resolved(ActionFacts facts) {
        return new ResourceScopeResolution(true, Objects.requireNonNull(facts, "facts"));
    }

    public boolean isResolved() { return resolved; }
    public ActionFacts getFacts() { return facts; }
}

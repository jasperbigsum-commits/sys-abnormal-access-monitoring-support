package io.github.jasper.monitoring.api.management;

import java.util.Objects;

/**
 * Shared guard for host implementations of management services.
 *
 * <p>Management service implementations use this guard with their trusted,
 * constructor-injected authorizer. Controller adapters must never select or
 * supply an authorizer.</p>
 */
public final class ManagementServiceSupport {
    private ManagementServiceSupport() {
    }

    /** Authorizes one resource in the actor's scope and returns the actor. */
    public static ManagementActor authorize(ManagementAuthorizer authorizer,
                                            ManagementActor actor,
                                            ManagementOperation operation,
                                            String resourceType,
                                            String resourceId) {
        Objects.requireNonNull(authorizer, "authorizer");
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(resourceType, "resourceType");
        Objects.requireNonNull(resourceId, "resourceId");
        if (resourceType.trim().isEmpty()) {
            throw new IllegalArgumentException("resourceType must not be blank");
        }
        if (resourceId.trim().isEmpty()) {
            throw new IllegalArgumentException("resourceId must not be blank");
        }
        authorizer.require(actor, operation,
            ManagementResource.of(resourceType, resourceId, actor.getSystemScope()));
        return actor;
    }
}

package io.github.jasper.monitoring.api.management;

import java.util.Objects;

/**
 * Shared guard for host implementations of management services.
 *
 * <p>Controller adapters should call {@link #authorize(ManagementAuthorizer,
 * ManagementActor, ManagementOperation, String, String)} before reading or
 * mutating a repository. This utility deliberately contains no web or
 * persistence dependency; it only standardizes the authorization boundary.</p>
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

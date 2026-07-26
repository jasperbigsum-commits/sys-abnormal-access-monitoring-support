package io.github.jasper.monitoring.api.management;
import java.util.Objects;
/** Authorization boundary invoked before every management data access or mutation. */
public interface ManagementAuthorizer {
    void authorize(ManagementActor actor, ManagementOperation operation, ManagementResource resource);
    static void require(ManagementActor actor, ManagementOperation operation, ManagementResource resource) { Objects.requireNonNull(actor,"actor"); Objects.requireNonNull(operation,"operation"); Objects.requireNonNull(resource,"resource"); }
}

package io.github.jasper.monitoring.api.code;

import java.util.Collection;

/** Host extension point for registering namespaced stable code definitions. */
public interface StableCodeContributor {
    Collection<CodeDefinition> definitions();
}

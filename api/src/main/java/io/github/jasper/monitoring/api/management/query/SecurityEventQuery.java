package io.github.jasper.monitoring.api.management.query;
import io.github.jasper.monitoring.api.management.ManagementPageRequest;
public final class SecurityEventQuery extends ManagementQuery { private SecurityEventQuery(ManagementPageRequest p){super(p);} public static SecurityEventQuery of(ManagementPageRequest p){return new SecurityEventQuery(p);} }

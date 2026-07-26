package io.github.jasper.monitoring.api.management.query;
import io.github.jasper.monitoring.api.management.ManagementPageRequest;
public final class ControlQuery extends ManagementQuery { private ControlQuery(ManagementPageRequest p){super(p);} public static ControlQuery of(ManagementPageRequest p){return new ControlQuery(p);} }

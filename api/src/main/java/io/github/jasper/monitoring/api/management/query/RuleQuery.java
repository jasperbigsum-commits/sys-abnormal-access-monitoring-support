package io.github.jasper.monitoring.api.management.query;
import io.github.jasper.monitoring.api.management.ManagementPageRequest;
public final class RuleQuery extends ManagementQuery { private RuleQuery(ManagementPageRequest p){super(p);} public static RuleQuery of(ManagementPageRequest p){return new RuleQuery(p);} }

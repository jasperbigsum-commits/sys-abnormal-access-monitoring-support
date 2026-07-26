package io.github.jasper.monitoring.api.management.query;
import io.github.jasper.monitoring.api.management.ManagementPageRequest;
public final class WhitelistQuery extends ManagementQuery { private WhitelistQuery(ManagementPageRequest p){super(p);} public static WhitelistQuery of(ManagementPageRequest p){return new WhitelistQuery(p);} }

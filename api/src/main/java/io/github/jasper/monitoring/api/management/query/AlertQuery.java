package io.github.jasper.monitoring.api.management.query;
import io.github.jasper.monitoring.api.management.ManagementPageRequest;
import java.util.Objects;
public final class AlertQuery { public enum Sort { CREATED_AT, SEVERITY, STATUS, ID } private final ManagementPageRequest page; private AlertQuery(ManagementPageRequest page){this.page=Objects.requireNonNull(page,"page");} public static AlertQuery of(ManagementPageRequest page){return new AlertQuery(page);} public ManagementPageRequest getPage(){return page;} }

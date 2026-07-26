package io.github.jasper.monitoring.api.management.query;
import io.github.jasper.monitoring.api.management.ManagementPageRequest;
import java.util.Objects;
public class ManagementQuery { private final ManagementPageRequest page; protected ManagementQuery(ManagementPageRequest page){this.page=Objects.requireNonNull(page,"page");} public ManagementPageRequest getPage(){return page;} }

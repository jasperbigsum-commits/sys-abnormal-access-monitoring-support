package io.github.jasper.monitoring.audit.spring3.persistence;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

/** Loads deterministic fictional reference data after the MyBatis mapper is available. */
@Component
public final class AuditFixtureDataInitializer implements InitializingBean {
    private final AuditFixtureRepository fixtures;
    public AuditFixtureDataInitializer(AuditFixtureRepository fixtures) { this.fixtures = fixtures; }
    @Override public void afterPropertiesSet() { fixtures.seed(); }
}

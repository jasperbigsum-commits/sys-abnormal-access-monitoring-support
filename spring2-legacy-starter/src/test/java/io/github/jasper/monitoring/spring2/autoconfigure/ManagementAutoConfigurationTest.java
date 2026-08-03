package io.github.jasper.monitoring.spring2.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.jasper.monitoring.api.management.AlertManagementService;
import io.github.jasper.monitoring.api.management.ControlManagementService;
import io.github.jasper.monitoring.api.management.ManagementAuthorizer;
import io.github.jasper.monitoring.api.management.RuleCatalogService;
import io.github.jasper.monitoring.api.management.SecurityEventQueryService;
import io.github.jasper.monitoring.api.management.WhitelistManagementService;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

class ManagementAutoConfigurationTest {
    private final WebApplicationContextRunner runner=new WebApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(AbnormalAccessMonitorAutoConfiguration.class))
        .withUserConfiguration(Persistence.class)
        .withPropertyValues("abnormal.access.monitor.authentication.subject-key="
            + "MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDE=");
    @Test void requiresAuthorizerBeforePublishingManagementServices(){runner.run(context->{
        assertThat(context).doesNotHaveBean(SecurityEventQueryService.class);
        assertThat(context).doesNotHaveBean(AlertManagementService.class);
    });}
    @Test void publishesAllControllerBoundariesWithTrustedAuthorizer(){runner.withUserConfiguration(Authorization.class).run(context->{
        assertThat(context).hasSingleBean(SecurityEventQueryService.class);assertThat(context).hasSingleBean(AlertManagementService.class);
        assertThat(context).hasSingleBean(RuleCatalogService.class);assertThat(context).hasSingleBean(WhitelistManagementService.class);
        assertThat(context).hasSingleBean(ControlManagementService.class);
    });}
    @Configuration static class Persistence { @Bean SqlSessionFactory sqlSessionFactory(){SqlSessionFactory f=Mockito.mock(SqlSessionFactory.class);Mockito.when(f.getConfiguration()).thenReturn(new org.apache.ibatis.session.Configuration());return f;} }
    @Configuration static class Authorization { @Bean ManagementAuthorizer managementAuthorizer(){return (actor,operation,resource)->{};} }
}

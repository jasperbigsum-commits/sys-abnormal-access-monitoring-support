package io.github.jasper.monitoring.spring3.autoconfigure;

import io.github.jasper.monitoring.api.action.ActionCatalog;
import io.github.jasper.monitoring.core.application.MonitoringRuntimePort;
import io.github.jasper.monitoring.core.application.MonitoringService;
import io.github.jasper.monitoring.core.application.control.ControlExecutionService;
import io.github.jasper.monitoring.core.port.MonitoringRepository;
import io.github.jasper.monitoring.mybatis.repository.MyBatisControlExecutionStore;
import io.github.jasper.monitoring.mybatis.repository.MyBatisMonitoringStore;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class TypedRuntimeAutoConfigurationTest {
    @Test
    void wiresTypedRuntimeAndDurableControlStateMachine() {
        new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(AbnormalAccessMonitorAutoConfiguration.class))
            .withUserConfiguration(PersistenceConfiguration.class)
            .run(context -> {
                assertThat(context).hasSingleBean(ActionCatalog.class);
                assertThat(context).hasSingleBean(MonitoringRuntimePort.class);
                assertThat(context).hasSingleBean(MonitoringService.class);
                assertThat(context).hasSingleBean(MyBatisMonitoringStore.class);
                assertThat(context).hasSingleBean(MyBatisControlExecutionStore.class);
                assertThat(context).hasSingleBean(ControlExecutionService.class);
                assertThat(context).hasSingleBean(TypedMonitorActionAspect.class);
            });
    }

    @Configuration(proxyBeanMethods = false)
    static class PersistenceConfiguration {
        @Bean SqlSessionFactory sqlSessionFactory() {
            SqlSessionFactory factory = Mockito.mock(SqlSessionFactory.class);
            Mockito.when(factory.getConfiguration()).thenReturn(new org.apache.ibatis.session.Configuration());
            return factory;
        }
        @Bean MonitoringRepository monitoringRepository() { return Mockito.mock(MonitoringRepository.class); }
    }
}

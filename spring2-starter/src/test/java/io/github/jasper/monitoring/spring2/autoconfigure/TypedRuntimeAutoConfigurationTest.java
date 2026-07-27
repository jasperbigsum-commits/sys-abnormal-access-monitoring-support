package io.github.jasper.monitoring.spring2.autoconfigure;

import io.github.jasper.monitoring.api.action.ActionCatalog;
import io.github.jasper.monitoring.api.rule.RuleCatalog;
import io.github.jasper.monitoring.api.ControlActionType;
import io.github.jasper.monitoring.api.action.BuiltInActions;
import io.github.jasper.monitoring.api.action.MonitorAction;
import io.github.jasper.monitoring.api.fact.ActionFact;
import io.github.jasper.monitoring.api.fact.BuiltInFacts;
import io.github.jasper.monitoring.core.domain.ControlCommand;
import io.github.jasper.monitoring.core.domain.ControlExecution;
import io.github.jasper.monitoring.core.port.ControlHandler;
import io.github.jasper.monitoring.core.application.MonitoringRuntimePort;
import io.github.jasper.monitoring.core.application.MonitoringService;
import io.github.jasper.monitoring.core.application.control.ControlExecutionService;
import io.github.jasper.monitoring.core.application.notification.NotificationDeliveryService;
import io.github.jasper.monitoring.core.application.authorization.ResourceAccessGuard;
import io.github.jasper.monitoring.spring.support.FrontendSignalRecorder;
import io.github.jasper.monitoring.spring.support.ActionFactExtractor;
import io.github.jasper.monitoring.spring.support.MonitorActionContractValidator;
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
                assertThat(context).hasSingleBean(NotificationDeliveryService.class);
                assertThat(context).hasSingleBean(
                    AbnormalAccessMonitorAutoConfiguration.NotificationRetryConfiguration.class);
                assertThat(context).hasSingleBean(TypedMonitorActionAspect.class);
                assertThat(context).hasSingleBean(ResourceAccessGuard.class);
                assertThat(context).hasSingleBean(FrontendSignalRecorder.class);
                assertThat(context).hasSingleBean(ActionFactExtractor.class);
                assertThat(context).hasSingleBean(MonitorActionContractValidator.class);
            });
    }

    @Test
    void disablesOnlyTheNotificationRetryDriverWhenConfigured() {
        new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(AbnormalAccessMonitorAutoConfiguration.class))
            .withUserConfiguration(PersistenceConfiguration.class)
            .withPropertyValues("abnormal.access.monitor.notification.retry-enabled=false")
            .run(context -> {
                assertThat(context).hasSingleBean(NotificationDeliveryService.class);
                assertThat(context).doesNotHaveBean(
                    AbnormalAccessMonitorAutoConfiguration.NotificationRetryConfiguration.class);
            });
    }

    @Test
    void enforceValidationUsesInjectedFrozenRuleCatalog() {
        new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(AbnormalAccessMonitorAutoConfiguration.class))
            .withUserConfiguration(PersistenceConfiguration.class, EmptyRuleCatalogConfiguration.class)
            .withPropertyValues("abnormal.access.monitor.mode=ENFORCE")
            .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void rejectsDuplicateExecutableControlHandlersAtStartup() {
        new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(AbnormalAccessMonitorAutoConfiguration.class))
            .withUserConfiguration(PersistenceConfiguration.class, DuplicateControlConfiguration.class)
            .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void rejectsActionFactNotOwnedByAnnotatedActionAtStartup() {
        new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(AbnormalAccessMonitorAutoConfiguration.class))
            .withUserConfiguration(PersistenceConfiguration.class, InvalidActionFactConfiguration.class)
            .run(context -> assertThat(context).hasFailed());
    }

    @Configuration(proxyBeanMethods = false)
    static class PersistenceConfiguration {
        @Bean SqlSessionFactory sqlSessionFactory() {
            SqlSessionFactory factory = Mockito.mock(SqlSessionFactory.class);
            Mockito.when(factory.getConfiguration()).thenReturn(new org.apache.ibatis.session.Configuration());
            return factory;
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class EmptyRuleCatalogConfiguration {
        @Bean RuleCatalog ruleCatalog() {
            RuleCatalog catalog = new RuleCatalog();
            catalog.freeze();
            return catalog;
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class DuplicateControlConfiguration {
        @Bean ControlHandler firstDenyHandler() { return denyHandler(); }
        @Bean ControlHandler secondDenyHandler() { return denyHandler(); }
    }

    @Configuration(proxyBeanMethods = false)
    static class InvalidActionFactConfiguration {
        @Bean InvalidActionFactService invalidActionFactService() {
            return new InvalidActionFactService();
        }
    }

    static class InvalidActionFactService {
        @MonitorAction(BuiltInActions.Query.class)
        public void query(@ActionFact(BuiltInFacts.DataCount.class) Long count) { }
    }

    private static ControlHandler denyHandler() {
        return new ControlHandler() {
            @Override public boolean supports(ControlActionType action) {
                return action == ControlActionType.DENY;
            }
            @Override public ControlExecution execute(ControlCommand command) {
                return ControlExecution.succeeded(command.getIdempotencyKey());
            }
        };
    }
}

package io.github.jasper.monitoring.spring3.autoconfigure;

import io.github.jasper.monitoring.api.AuthorizationDecision;
import io.github.jasper.monitoring.api.IdentityContext;
import io.github.jasper.monitoring.api.MonitoringRequestContext;
import io.github.jasper.monitoring.api.ResourceScopeRequest;
import io.github.jasper.monitoring.api.error.MonitoringConfigurationException;
import io.github.jasper.monitoring.api.error.MonitoringErrorCode;
import io.github.jasper.monitoring.core.application.MonitoringService;
import io.github.jasper.monitoring.core.application.authorization.ResourceAccessGuard;
import io.github.jasper.monitoring.spring.support.control.GenericIpControlHandler;
import io.github.jasper.monitoring.spring.support.control.LocalIpControlState;
import io.github.jasper.monitoring.spring.support.MonitoringRecorder;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class AbnormalAccessMonitorAutoConfigurationTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(AbnormalAccessMonitorAutoConfiguration.class))
        .withUserConfiguration(PersistenceConfiguration.class);
    private final WebApplicationContextRunner webContextRunner = new WebApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(AbnormalAccessMonitorAutoConfiguration.class))
        .withUserConfiguration(PersistenceConfiguration.class);

    @Test
    void failsWhenSqlSessionFactoryIsUnavailable() {
        new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(AbnormalAccessMonitorAutoConfiguration.class))
            .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void createsTypedRuntimeOutsideSpringMvc() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(MonitoringService.class);
            assertThat(context).doesNotHaveBean(TypedMonitorActionAspect.class);
            assertThat(context).doesNotHaveBean(MonitoringRecorder.class);
        });
    }

    @Test
    void providesAConciseRecorderInServletApplications() {
        webContextRunner.run(context -> assertThat(context).hasSingleBean(MonitoringRecorder.class));
    }

    @Test
    void deniesResourceAccessWhenHostAuthorizerIsNotConfigured() {
        contextRunner.run(context -> {
            MonitoringRequestContext request = MonitoringRequestContext.builder()
                .method("GET").path("/orders/o-1").sourceIp("203.0.113.7").requestId("req-1").build();
            AuthorizationDecision decision = context.getBean(ResourceAccessGuard.class).authorize(
                IdentityContext.anonymous(), new ResourceScopeRequest(request, "order", "o-1", "org-a"));
            assertThat(decision.isAllowed()).isFalse();
            assertThat(decision.getReasonCode()).isEqualTo("RESOURCE_SCOPE_AUTHORIZER_NOT_CONFIGURED");
        });
    }

    @Test
    void leavesGenericIpControlDisabledByDefault() {
        webContextRunner.run(context -> {
            assertThat(context).doesNotHaveBean(IpControlFilter.class);
            assertThat(context).doesNotHaveBean(LocalIpControlState.class);
            assertThat(context).doesNotHaveBean(GenericIpControlHandler.class);
        });
    }

    @Test
    void refusesEnforceModeWithoutExecutableControlHandler() {
        contextRunner.withPropertyValues("abnormal.access.monitor.mode=ENFORCE")
            .run(context -> {
                assertThat(context).hasFailed();
                assertThat(findCause(context.getStartupFailure()).getErrorCode())
                    .isEqualTo(MonitoringErrorCode.ENFORCEMENT_HANDLER_REQUIRED);
            });
    }

    @Test
    void rejectsGenericIpControlOutsideEnforceMode() {
        webContextRunner.withPropertyValues(validIpControlProperties())
            .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void rejectsIncompleteGenericIpControlConfiguration() {
        webContextRunner.withPropertyValues(
                "abnormal.access.monitor.mode=ENFORCE",
                "abnormal.access.monitor.ip-control.enabled=true")
            .run(context -> {
                assertThat(context).hasFailed();
                MonitoringConfigurationException failure = findCause(context.getStartupFailure());
                assertThat(failure).isNotNull();
                assertThat(failure.getErrorCode()).isEqualTo(MonitoringErrorCode.INVALID_FIELD_VALUE);
            });
    }

    @Test
    void registersGenericIpControlInEnforceMode() {
        webContextRunner.withPropertyValues(validIpControlProperties())
            .withPropertyValues("abnormal.access.monitor.mode=ENFORCE")
            .withUserConfiguration(RequiredControlConfiguration.class)
            .run(context -> {
                assertThat(context).hasSingleBean(IpControlFilter.class);
                assertThat(context).hasSingleBean(LocalIpControlState.class);
                assertThat(context).hasSingleBean(GenericIpControlHandler.class);
            });
    }

    private static String[] validIpControlProperties() {
        return new String[] {
            "abnormal.access.monitor.ip-control.enabled=true",
            "abnormal.access.monitor.ip-control.protected-paths[0]=/api/**",
            "abnormal.access.monitor.ip-control.excluded-paths[0]=/api/health",
            "abnormal.access.monitor.ip-control.rule-ids[0]=RULE-IP",
            "abnormal.access.monitor.ip-control.permits-per-window=2",
            "abnormal.access.monitor.ip-control.window=30s",
            "abnormal.access.monitor.ip-control.max-ttl=5m",
            "abnormal.access.monitor.ip-control.capacity=100"
        };
    }

    private static MonitoringConfigurationException findCause(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof MonitoringConfigurationException) {
                return (MonitoringConfigurationException) current;
            }
            current = current.getCause();
        }
        return null;
    }

    @Configuration(proxyBeanMethods = false)
    static class PersistenceConfiguration {
        @Bean
        SqlSessionFactory sqlSessionFactory() {
            SqlSessionFactory factory = Mockito.mock(SqlSessionFactory.class);
            Mockito.when(factory.getConfiguration()).thenReturn(new org.apache.ibatis.session.Configuration());
            return factory;
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class RequiredControlConfiguration {
        @Bean
        io.github.jasper.monitoring.core.port.ControlHandler remainingBuiltInControls() {
            return new io.github.jasper.monitoring.core.port.ControlHandler() {
                @Override
                public boolean supports(io.github.jasper.monitoring.api.ControlActionType action) {
                    return action == io.github.jasper.monitoring.api.ControlActionType.REQUIRE_CAPTCHA
                        || action == io.github.jasper.monitoring.api.ControlActionType.REVOKE_SESSION
                        || action == io.github.jasper.monitoring.api.ControlActionType.REQUIRE_MFA
                        || action == io.github.jasper.monitoring.api.ControlActionType.REQUIRE_APPROVAL;
                }

                @Override
                public io.github.jasper.monitoring.core.domain.ControlExecution execute(
                        io.github.jasper.monitoring.core.domain.ControlCommand command) {
                    return io.github.jasper.monitoring.core.domain.ControlExecution.succeeded(
                        command.getIdempotencyKey());
                }
            };
        }
    }
}

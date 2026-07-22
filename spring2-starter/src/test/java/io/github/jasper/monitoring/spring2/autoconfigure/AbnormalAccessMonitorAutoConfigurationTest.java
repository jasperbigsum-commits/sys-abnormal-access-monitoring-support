package io.github.jasper.monitoring.spring2.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.jasper.monitoring.api.MonitoringRequestContext;
import io.github.jasper.monitoring.api.ControlActionType;
import io.github.jasper.monitoring.api.AuthorizationDecision;
import io.github.jasper.monitoring.api.IdentityContext;
import io.github.jasper.monitoring.api.ResourceScopeRequest;
import io.github.jasper.monitoring.api.SecurityEventDraft;
import io.github.jasper.monitoring.api.SecurityEventResult;
import io.github.jasper.monitoring.api.SecurityEventType;
import io.github.jasper.monitoring.core.DefaultSecurityMonitor;
import io.github.jasper.monitoring.core.AlertLifecycleService;
import io.github.jasper.monitoring.core.ControlCommand;
import io.github.jasper.monitoring.core.ControlExecution;
import io.github.jasper.monitoring.core.ControlHandler;
import io.github.jasper.monitoring.core.InMemoryMonitoringRepository;
import io.github.jasper.monitoring.core.MonitoringOutcome;
import io.github.jasper.monitoring.core.MonitoringRepository;
import io.github.jasper.monitoring.core.ResourceAccessGuard;
import java.time.Instant;
import javax.servlet.http.HttpServletRequest;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

class AbnormalAccessMonitorAutoConfigurationTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(autoConfiguration()));

    @Test
    void createsAnInMemoryObserveMonitorWhenNoSqlSessionFactoryIsAvailable() {
        contextRunner.withPropertyValues("abnormal.access.monitor.system-id=orders")
            .run(context -> {
                assertThat(context).hasSingleBean(DefaultSecurityMonitor.class);
                assertThat(context).hasSingleBean(AlertLifecycleService.class);
                assertThat(context).hasSingleBean(ResourceAccessGuard.class);
                assertThat(context.getBean(MonitoringRepository.class)).isInstanceOf(InMemoryMonitoringRepository.class);

                MonitoringOutcome outcome = context.getBean(DefaultSecurityMonitor.class).record(disabledLoginFailure());

                assertThat(outcome.getEvent().getSystemId()).isEqualTo("orders");
                assertThat(outcome.getControls()).isEmpty();
            });
    }

    @Test
    void usesMyBatisRepositoryWhenSqlSessionFactoryIsAvailable() {
        contextRunner.withUserConfiguration(SqlSessionFactoryConfiguration.class)
            .run(context -> assertThat(context.getBean(MonitoringRepository.class).getClass().getName())
                .isEqualTo("io.github.jasper.monitoring.mybatis.MyBatisMonitoringRepository"));
    }

    @Test
    void deniesResourceAccessWhenTheHostAuthorizerIsNotConfigured() {
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
    void refusesEnforceModeWithoutAHostControlHandler() {
        contextRunner.withPropertyValues(
                "abnormal.access.monitor.system-id=orders",
                "abnormal.access.monitor.mode=ENFORCE")
            .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void enablesControlWhenAHostControlHandlerIsConfigured() {
        contextRunner.withUserConfiguration(ControlHandlerConfiguration.class)
            .withPropertyValues(
                "abnormal.access.monitor.system-id=orders",
                "abnormal.access.monitor.mode=ENFORCE")
            .run(context -> assertThat(context.getBean(DefaultSecurityMonitor.class).record(disabledLoginFailure()).getControls())
                .hasSize(1));
    }

    @Test
    void collectsTrustedRequestMetadataUnlessFrontendCollectionIsDisabled() throws Exception {
        contextRunner.withPropertyValues("abnormal.access.monitor.trusted-proxies=10.0.0.0/8")
            .run(context -> {
                HandlerInterceptor interceptor = context.getBean(HandlerInterceptor.class);
                MockHttpServletRequest request = new MockHttpServletRequest("GET", "/customers/42");
                request.setRemoteAddr("10.10.10.10");
                request.addHeader("X-Forwarded-For", "198.51.100.20");
                request.addHeader("X-Request-Id", "request-42");

                assertThat(interceptor.preHandle(request, new MockHttpServletResponse(), new Object())).isTrue();
                MonitoringRequestContext requestContext = (MonitoringRequestContext) request.getAttribute(
                    "io.github.jasper.monitoring.request-context");

                assertThat(requestContext.getSourceIp()).isEqualTo("198.51.100.20");
                assertThat(requestContext.getRequestId()).isEqualTo("request-42");
            });

        contextRunner.withPropertyValues("abnormal.access.monitor.frontend.enabled=false")
            .run(context -> assertThat(context.getBeansOfType(HandlerInterceptor.class)).isEmpty());
    }

    private static Class<?> autoConfiguration() {
        try {
            return Class.forName("io.github.jasper.monitoring.spring2.autoconfigure.AbnormalAccessMonitorAutoConfiguration");
        } catch (ClassNotFoundException exception) {
            throw new AssertionError("Spring Boot 2 auto-configuration is missing", exception);
        }
    }

    private static SecurityEventDraft disabledLoginFailure() {
        return SecurityEventDraft.builder()
            .eventType(SecurityEventType.LOGIN_FAILURE)
            .action("login")
            .result(SecurityEventResult.FAILURE)
            .sourceIp("198.51.100.20")
            .requestId("request-1")
            .userId("admin")
            .occurredAt(Instant.parse("2025-01-01T00:00:00Z"))
            .attribute("account_status", "DISABLED")
            .build();
    }

    @Configuration(proxyBeanMethods = false)
    static class SqlSessionFactoryConfiguration {
        @Bean
        SqlSessionFactory sqlSessionFactory() {
            SqlSessionFactory factory = Mockito.mock(SqlSessionFactory.class);
            Mockito.when(factory.getConfiguration()).thenReturn(new org.apache.ibatis.session.Configuration());
            return factory;
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class ControlHandlerConfiguration {
        @Bean
        ControlHandler controlHandler() {
            return new ControlHandler() {
                @Override
                public boolean supports(ControlActionType action) { return action == ControlActionType.DENY; }
                @Override
                public ControlExecution execute(ControlCommand command) {
                    return ControlExecution.succeeded(command.getIdempotencyKey());
                }
            };
        }
    }
}

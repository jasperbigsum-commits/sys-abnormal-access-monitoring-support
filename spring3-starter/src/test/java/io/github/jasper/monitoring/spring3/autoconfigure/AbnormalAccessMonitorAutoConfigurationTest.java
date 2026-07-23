package io.github.jasper.monitoring.spring3.autoconfigure;

import io.github.jasper.monitoring.core.domain.SecurityEvent;
import io.github.jasper.monitoring.core.domain.RuleMatch;
import static org.assertj.core.api.Assertions.assertThat;
import io.github.jasper.monitoring.api.AccountType;
import io.github.jasper.monitoring.api.MonitoringRequestContext;
import io.github.jasper.monitoring.api.ControlActionType;
import io.github.jasper.monitoring.api.ControlTrigger;
import io.github.jasper.monitoring.api.MonitorAction;
import io.github.jasper.monitoring.api.AuthorizationDecision;
import io.github.jasper.monitoring.api.IdentityContext;
import io.github.jasper.monitoring.api.ResourceScopeRequest;
import io.github.jasper.monitoring.api.SecurityEventDraft;
import io.github.jasper.monitoring.api.SecurityEventResult;
import io.github.jasper.monitoring.api.SecurityEventType;
import io.github.jasper.monitoring.api.TrustedProxyResolver;
import io.github.jasper.monitoring.core.application.DefaultSecurityMonitor;
import io.github.jasper.monitoring.core.domain.rule.DetectionRule;
import io.github.jasper.monitoring.core.application.AlertLifecycleService;
import io.github.jasper.monitoring.core.domain.ControlCommand;
import io.github.jasper.monitoring.core.domain.ControlExecution;
import io.github.jasper.monitoring.core.port.ControlHandler;
import io.github.jasper.monitoring.core.infrastructure.memory.InMemoryMonitoringRepository;
import io.github.jasper.monitoring.core.application.rule.InternalRuleContributor;
import io.github.jasper.monitoring.core.application.rule.InternalRuleRegistry;
import io.github.jasper.monitoring.core.application.MonitoringOutcome;
import io.github.jasper.monitoring.core.port.MonitoringRepository;
import io.github.jasper.monitoring.core.application.authorization.ResourceAccessGuard;
import io.github.jasper.monitoring.core.application.SecurityMonitor;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;

class AbnormalAccessMonitorAutoConfigurationTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(autoConfiguration()));
    private final WebApplicationContextRunner webContextRunner = new WebApplicationContextRunner()
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
    void freezesHostContributedInternalRulesBeforeCreatingTheMonitor() {
        contextRunner.withUserConfiguration(InternalRuleConfiguration.class)
            .run(context -> {
                InternalRuleRegistry registry = context.getBean(InternalRuleRegistry.class);

                assertThat(registry.isFrozen()).isTrue();
                assertThat(registry.entries()).extracting(entry -> entry.getRuleId()).contains("HOST-01");
                assertThat(registry.entries()).allMatch(entry -> !entry.isMutable());
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
    void doesNotLoadMvcIntegrationOutsideAServletWebApplication() {
        contextRunner.run(context -> {
            assertThat(context).doesNotHaveBean(RequestMetadataInterceptor.class);
            assertThat(context).doesNotHaveBean(AnnotatedActionMonitoringInterceptor.class);
        });
    }

    @Test
    void skipsMvcIntegrationWhenSpringMvcIsUnavailable() {
        webContextRunner.withClassLoader(new FilteredClassLoader("org.springframework.web.servlet.HandlerInterceptor"))
            .run(context -> {
                assertThat(context).doesNotHaveBean(RequestMetadataInterceptor.class);
                assertThat(context).doesNotHaveBean(AnnotatedActionMonitoringInterceptor.class);
            });
    }

    @Test
    void separatesFrontendMetadataCollectionFromAnnotationInstrumentation() throws Exception {
        webContextRunner.withPropertyValues("abnormal.access.monitor.trusted-proxies=10.0.0.0/8")
            .run(context -> {
                RequestMetadataInterceptor interceptor = context.getBean(RequestMetadataInterceptor.class);
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

        webContextRunner.withPropertyValues("abnormal.access.monitor.frontend.enabled=false")
            .run(context -> {
                assertThat(context).hasSingleBean(RequestMetadataInterceptor.class);
                assertThat(context).hasSingleBean(AnnotatedActionMonitoringInterceptor.class);
            });

        webContextRunner.withPropertyValues("abnormal.access.monitor.instrumentation.enabled=false")
            .run(context -> assertThat(context).doesNotHaveBean(AnnotatedActionMonitoringInterceptor.class));
    }

    @Test
    void recordsAnnotatedMvcActionsWhenFrontendMetadataCollectionIsDisabled() throws Exception {
        webContextRunner.withUserConfiguration(CapturingMonitorConfiguration.class)
            .withPropertyValues("abnormal.access.monitor.frontend.enabled=false")
            .run(context -> {
                AnnotatedActionMonitoringInterceptor interceptor = context.getBean(AnnotatedActionMonitoringInterceptor.class);
                CapturingSecurityMonitor monitor = context.getBean(CapturingSecurityMonitor.class);
                HandlerMethod handler = new HandlerMethod(new AnnotatedController(),
                    AnnotatedController.class.getMethod("exportReport"));

                MockHttpServletRequest successRequest = new MockHttpServletRequest("GET", "/reports/monthly");
                successRequest.addHeader("X-Request-Id", "request-success");
                MockHttpServletResponse successResponse = new MockHttpServletResponse();
                interceptor.preHandle(successRequest, successResponse, handler);
                interceptor.afterCompletion(successRequest, successResponse, handler, null);

                MockHttpServletRequest deniedRequest = new MockHttpServletRequest("GET", "/reports/monthly");
                deniedRequest.addHeader("X-Request-Id", "request-denied");
                MockHttpServletResponse deniedResponse = new MockHttpServletResponse();
                deniedResponse.setStatus(403);
                interceptor.preHandle(deniedRequest, deniedResponse, handler);
                interceptor.afterCompletion(deniedRequest, deniedResponse, handler,
                    new IllegalStateException("must not be recorded"));

                MockHttpServletRequest failedRequest = new MockHttpServletRequest("GET", "/reports/monthly");
                failedRequest.addHeader("X-Request-Id", "request-failed");
                MockHttpServletResponse failedResponse = new MockHttpServletResponse();
                interceptor.preHandle(failedRequest, failedResponse, handler);
                interceptor.afterCompletion(failedRequest, failedResponse, handler,
                    new IllegalStateException("must not be recorded"));

                MockHttpServletRequest serverFailureRequest = new MockHttpServletRequest("GET", "/reports/monthly");
                serverFailureRequest.addHeader("X-Request-Id", "request-server-failed");
                MockHttpServletResponse serverFailureResponse = new MockHttpServletResponse();
                serverFailureResponse.setStatus(500);
                interceptor.preHandle(serverFailureRequest, serverFailureResponse, handler);
                interceptor.afterCompletion(serverFailureRequest, serverFailureResponse, handler, null);

                assertThat(monitor.events).hasSize(4);
                assertThat(monitor.events.get(0).getEventType()).isEqualTo(SecurityEventType.EXPORT);
                assertThat(monitor.events.get(0).getAction()).isEqualTo("export-report");
                assertThat(monitor.events.get(0).getResourceType()).isEqualTo("report");
                assertThat(monitor.events.get(0).getUserId()).isEqualTo("operator-1");
                assertThat(monitor.events.get(0).getResult()).isEqualTo(SecurityEventResult.SUCCESS);
                assertThat(monitor.events.get(0).getReasonCode()).isNull();
                assertThat(monitor.events.get(1).getResult()).isEqualTo(SecurityEventResult.DENIED);
                assertThat(monitor.events.get(1).getReasonCode()).isEqualTo("HTTP_403");
                assertThat(monitor.events.get(2).getResult()).isEqualTo(SecurityEventResult.FAILURE);
                assertThat(monitor.events.get(2).getReasonCode()).isEqualTo("HANDLER_EXCEPTION");
                assertThat(monitor.events.get(3).getResult()).isEqualTo(SecurityEventResult.FAILURE);
                assertThat(monitor.events.get(3).getReasonCode()).isEqualTo("HTTP_500");
            });
    }

    @Test
    void ignoresMetadataFailureWithoutInterruptingAnAnnotatedAction() throws Exception {
        webContextRunner.withUserConfiguration(CapturingMonitorConfiguration.class, FailingTrustedProxyConfiguration.class)
            .run(context -> {
                AnnotatedActionMonitoringInterceptor interceptor = context.getBean(AnnotatedActionMonitoringInterceptor.class);
                CapturingSecurityMonitor monitor = context.getBean(CapturingSecurityMonitor.class);
                RequestMetadataInterceptor metadata = context.getBean(RequestMetadataInterceptor.class);
                HandlerMethod handler = new HandlerMethod(new AnnotatedController(),
                    AnnotatedController.class.getMethod("exportReport"));
                MockHttpServletRequest request = new MockHttpServletRequest("GET", "/reports/monthly");
                MockHttpServletResponse response = new MockHttpServletResponse();

                assertThat(metadata.preHandle(request, response, handler)).isTrue();
                assertThat(interceptor.preHandle(request, response, handler)).isTrue();
                interceptor.afterCompletion(request, response, handler, null);

                assertThat(request.getAttribute(RequestMetadataInterceptor.REQUEST_CONTEXT_ATTRIBUTE)).isNull();
                assertThat(monitor.events).isEmpty();
            });
    }

    @Test
    void bindsAnnotatedControlTriggerForEnforceMode() {
        contextRunner.withUserConfiguration(AnnotatedControlTriggerConfiguration.class)
            .withPropertyValues("abnormal.access.monitor.system-id=orders", "abnormal.access.monitor.mode=ENFORCE")
            .run(context -> {
                AnnotatedControlTarget target = context.getBean(AnnotatedControlTarget.class);
                MonitoringOutcome outcome = context.getBean(DefaultSecurityMonitor.class).record(disabledLoginFailure());

                assertThat(outcome.getControls()).hasSize(1);
                assertThat(target.command).isNotNull();
                assertThat(target.command.getAction()).isEqualTo(ControlActionType.DENY);
                assertThat(target.monitor).isNotNull();
            });
    }

    @Test
    void rejectsDuplicateAnnotatedControlTriggers() {
        contextRunner.withUserConfiguration(DuplicateControlTriggerConfiguration.class)
            .run(context -> assertThat(context).hasFailed());
    }

    private static Class<?> autoConfiguration() {
        try {
            return Class.forName("io.github.jasper.monitoring.spring3.autoconfigure.AbnormalAccessMonitorAutoConfiguration");
        } catch (ClassNotFoundException exception) {
            throw new AssertionError("Spring Boot 3 auto-configuration is missing", exception);
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

    @Configuration(proxyBeanMethods = false)
    static class CapturingMonitorConfiguration {
        @Bean
        CapturingSecurityMonitor capturingSecurityMonitor() {
            return new CapturingSecurityMonitor();
        }

        @Bean
        io.github.jasper.monitoring.api.IdentityContextProvider identityContextProvider() {
            return request -> new IdentityContext("operator-1", AccountType.PERSON,
                Collections.singleton("OPS"), "session-hash");
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class InternalRuleConfiguration {
        @Bean
        InternalRuleContributor hostRuleContributor() {
            return registrar -> registrar.register(new DetectionRule() {
                @Override
                public String getRuleId() {
                    return "HOST-01";
                }

                @Override
                public Optional<io.github.jasper.monitoring.core.domain.RuleMatch> evaluate(
                    io.github.jasper.monitoring.core.domain.SecurityEvent event,
                    List<io.github.jasper.monitoring.core.domain.SecurityEvent> history) {
                    return Optional.empty();
                }
            });
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class AnnotatedControlTriggerConfiguration {
        @Bean
        AnnotatedControlTarget annotatedControlTarget(SecurityMonitor monitor) {
            return new AnnotatedControlTarget(monitor);
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class FailingTrustedProxyConfiguration {
        @Bean
        TrustedProxyResolver trustedProxyResolver() {
            return (directAddress, forwardedForHeader) -> {
                throw new IllegalStateException("not recorded");
            };
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class DuplicateControlTriggerConfiguration {
        @Bean
        FirstDuplicateControl firstDuplicateControl() {
            return new FirstDuplicateControl();
        }

        @Bean
        SecondDuplicateControl secondDuplicateControl() {
            return new SecondDuplicateControl();
        }
    }

    @MonitorAction(eventType = SecurityEventType.UPDATE, action = "type-default")
    static class AnnotatedController {
        @MonitorAction(eventType = SecurityEventType.EXPORT, action = "export-report", resourceType = "report")
        public void exportReport() {
        }
    }

    static class AnnotatedControlTarget {
        private ControlCommand command;
        private final SecurityMonitor monitor;

        AnnotatedControlTarget(SecurityMonitor monitor) {
            this.monitor = monitor;
        }

        @ControlTrigger(ControlActionType.DENY)
        public void deny(ControlCommand value) {
            command = value;
        }
    }

    static class FirstDuplicateControl {
        @ControlTrigger(ControlActionType.DENY)
        public void deny(ControlCommand command) {
        }
    }

    static class SecondDuplicateControl {
        @ControlTrigger(ControlActionType.DENY)
        public void deny(ControlCommand command) {
        }
    }

    static class CapturingSecurityMonitor implements SecurityMonitor {
        private final List<SecurityEventDraft> events = new ArrayList<SecurityEventDraft>();

        @Override
        public MonitoringOutcome record(SecurityEventDraft draft) {
            events.add(draft);
            return null;
        }
    }
}

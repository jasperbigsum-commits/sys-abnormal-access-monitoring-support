package io.github.jasper.monitoring.spring2.autoconfigure;

import io.github.jasper.monitoring.core.domain.RuleMatch;
import static org.assertj.core.api.Assertions.assertThat;
import io.github.jasper.monitoring.api.AccountType;
import io.github.jasper.monitoring.api.EventEnricher;
import io.github.jasper.monitoring.api.MonitorActionDefinition;
import io.github.jasper.monitoring.api.MonitoringRequestContext;
import io.github.jasper.monitoring.api.ControlActionType;
import io.github.jasper.monitoring.api.AuthorizationDecision;
import io.github.jasper.monitoring.api.ControlTrigger;
import io.github.jasper.monitoring.api.IdentityContext;
import io.github.jasper.monitoring.api.IdentityContextProvider;
import io.github.jasper.monitoring.api.MonitorAction;
import io.github.jasper.monitoring.api.ResourceScopeRequest;
import io.github.jasper.monitoring.api.SecurityEventDraft;
import io.github.jasper.monitoring.api.SecurityEventResult;
import io.github.jasper.monitoring.api.SecurityEventType;
import io.github.jasper.monitoring.core.application.DefaultSecurityMonitor;
import io.github.jasper.monitoring.core.application.ActionEventRecorder;
import io.github.jasper.monitoring.core.application.control.ControlHandlerRegistry;
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
import io.github.jasper.monitoring.core.domain.SecurityEvent;
import io.github.jasper.monitoring.core.application.SecurityMonitor;
import io.github.jasper.monitoring.spring.support.control.GenericIpControlHandler;
import io.github.jasper.monitoring.spring.support.control.LocalIpControlState;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.Optional;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.AsyncHandlerInterceptor;
import org.slf4j.MDC;

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
    void automaticallyAppliesHostEventEnricherBeans() {
        contextRunner.withUserConfiguration(EventEnricherConfiguration.class).run(context -> {
            context.getBean(ActionEventRecorder.class).record(
                MonitorActionDefinition.builder("report:read").build(),
                MonitoringRequestContext.builder().method("GET").path("/reports/r-1")
                    .sourceIp("203.0.113.7").requestId("request-enricher").build(),
                IdentityContext.anonymous(), SecurityEventResult.SUCCESS, null);

            assertThat(((InMemoryMonitoringRepository) context.getBean(MonitoringRepository.class))
                .getEvents().get(0).getOrgScope()).isEqualTo("org-enriched");
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
    void registersDefaultTriggersAfterHostControlHandlers() {
        contextRunner.withUserConfiguration(ControlHandlerConfiguration.class)
            .run(context -> {
                ControlHandlerRegistry registry = context.getBean(ControlHandlerRegistry.class);

                assertThat(registry.find(ControlActionType.DENY).get())
                    .isSameAs(context.getBean(ControlHandler.class));
                ControlExecution fallback = registry.find(ControlActionType.RATE_LIMIT).get().execute(
                    new ControlCommand("alert-1:RATE_LIMIT", "alert-1", "ip:203.0.113.8",
                        ControlActionType.RATE_LIMIT, Instant.parse("2026-07-24T00:00:00Z")));
                assertThat(fallback.getStatus().name()).isEqualTo("SKIPPED");
                assertThat(fallback.getFailureReason()).isEqualTo("DEFAULT_TRIGGER_REQUIRES_HOST_HANDLER:RATE_LIMIT");
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
    void doesNotRegisterGenericIpControlOutsideAServletApplication() {
        contextRunner.withPropertyValues(validIpControlProperties())
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context).doesNotHaveBean(LocalIpControlState.class);
                assertThat(context).doesNotHaveBean(GenericIpControlHandler.class);
                assertThat(context.getBean(ControlHandlerRegistry.class).isEmpty()).isTrue();
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
            .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void registersGenericIpControlOnlyAsTheGenericRegistryTier() {
        webContextRunner.withUserConfiguration(ControlHandlerConfiguration.class)
            .withPropertyValues(validIpControlProperties())
            .withPropertyValues("abnormal.access.monitor.mode=ENFORCE")
            .run(context -> {
                assertThat(context).hasSingleBean(IpControlFilter.class);
                assertThat(context).hasSingleBean(LocalIpControlState.class);
                assertThat(context).hasSingleBean(GenericIpControlHandler.class);

                ControlHandlerRegistry registry = context.getBean(ControlHandlerRegistry.class);
                assertThat(registry.find(ControlActionType.DENY).get())
                    .isSameAs(context.getBean("controlHandler", ControlHandler.class));
                assertThat(registry.find(ControlActionType.RATE_LIMIT).get())
                    .isSameAs(context.getBean(GenericIpControlHandler.class));
            });
    }

    @Test
    void createsCoreMonitoringWithoutSpringWebMvc() {
        webContextRunner.withClassLoader(new FilteredClassLoader("org.springframework.web.servlet"))
            .run(context -> {
                assertThat(context).hasSingleBean(DefaultSecurityMonitor.class);
                assertThat(context.containsBean("abnormalAccessRequestMetadataInterceptor")).isFalse();
                assertThat(context.containsBean("abnormalAccessAnnotatedMonitoringInterceptor")).isFalse();
            });
    }

    @Test
    void collectsTrustedRequestMetadataAndKeepsInstrumentationIndependentFromFrontendCollection() throws Exception {
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
                assertThat(context).hasSingleBean(AnnotatedMonitoringInterceptor.class);
            });

        webContextRunner.withPropertyValues("abnormal.access.monitor.instrumentation.enabled=false")
            .run(context -> {
                assertThat(context).hasSingleBean(RequestMetadataInterceptor.class);
                assertThat(context.getBeansOfType(AnnotatedMonitoringInterceptor.class)).isEmpty();
            });
    }

    @Test
    void doesNotBlockRequestsWhenMetadataResolutionFails() throws Exception {
        RequestMetadataInterceptor interceptor = new RequestMetadataInterceptor(
            (directAddress, forwardedFor) -> { throw new IllegalStateException("resolver failure"); },
            request -> IdentityContext.anonymous());
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/reports/export");

        assertThat(interceptor.preHandle(request, new MockHttpServletResponse(), new Object())).isTrue();
        assertThat(request.getAttribute(RequestMetadataInterceptor.REQUEST_CONTEXT_ATTRIBUTE)).isNull();
        assertThat(request.getAttribute(RequestMetadataInterceptor.IDENTITY_CONTEXT_ATTRIBUTE)).isNull();
    }

    @Test
    void usesOneTraceIdForMdcAndMonitoringContextThenRestoresThePriorMdcValue() throws Exception {
        MDC.put("traceId", "upstream-trace");
        try {
            webContextRunner.run(context -> {
                RequestMetadataInterceptor interceptor = context.getBean(RequestMetadataInterceptor.class);
                MockHttpServletRequest request = new MockHttpServletRequest("GET", "/reports/export");
                request.addHeader("X-Trace-Id", "monitor-trace");
                MockHttpServletResponse response = new MockHttpServletResponse();

                interceptor.preHandle(request, response, new Object());

                MonitoringRequestContext requestContext = (MonitoringRequestContext) request.getAttribute(
                    RequestMetadataInterceptor.REQUEST_CONTEXT_ATTRIBUTE);
                assertThat(requestContext.getTraceId()).isEqualTo("monitor-trace");
                assertThat(MDC.get("traceId")).isEqualTo("monitor-trace");

                interceptor.afterCompletion(request, response, new Object(), null);
                assertThat(MDC.get("traceId")).isEqualTo("upstream-trace");
            });
        } finally {
            MDC.remove("traceId");
        }
    }

    @Test
    void restoresAndRebindsMdcAcrossAsyncDispatches() throws Exception {
        MDC.put("traceId", "upstream-initial");
        try {
            webContextRunner.run(context -> {
                RequestMetadataInterceptor interceptor = context.getBean(RequestMetadataInterceptor.class);
                assertThat(interceptor).isInstanceOf(AsyncHandlerInterceptor.class);
                AsyncHandlerInterceptor asyncInterceptor = (AsyncHandlerInterceptor) (Object) interceptor;
                MockHttpServletRequest request = new MockHttpServletRequest("GET", "/reports/async");
                request.addHeader("X-Trace-Id", "monitor-trace");
                MockHttpServletResponse response = new MockHttpServletResponse();
                Object handler = new Object();

                interceptor.preHandle(request, response, handler);
                Object requestContext = request.getAttribute(RequestMetadataInterceptor.REQUEST_CONTEXT_ATTRIBUTE);
                Object identityContext = request.getAttribute(RequestMetadataInterceptor.IDENTITY_CONTEXT_ATTRIBUTE);
                assertThat(MDC.get("traceId")).isEqualTo("monitor-trace");

                asyncInterceptor.afterConcurrentHandlingStarted(request, response, handler);
                assertThat(MDC.get("traceId")).isEqualTo("upstream-initial");

                MDC.put("traceId", "upstream-dispatch");
                interceptor.preHandle(request, response, handler);
                assertThat(request.getAttribute(RequestMetadataInterceptor.REQUEST_CONTEXT_ATTRIBUTE))
                    .isSameAs(requestContext);
                assertThat(request.getAttribute(RequestMetadataInterceptor.IDENTITY_CONTEXT_ATTRIBUTE))
                    .isSameAs(identityContext);
                assertThat(MDC.get("traceId")).isEqualTo("monitor-trace");

                interceptor.afterCompletion(request, response, handler, null);
                assertThat(MDC.get("traceId")).isEqualTo("upstream-dispatch");
            });
        } finally {
            MDC.remove("traceId");
        }
    }

    @Test
    void usesJdkProxyForAFinalInterfaceBasedAnnotatedBean() {
        webContextRunner.withUserConfiguration(FinalActionConfiguration.class)
            .run(context -> {
                assertThat(context).hasNotFailed();
                FinalAction action = context.getBean(FinalAction.class);
                assertThat(AopUtils.isJdkDynamicProxy(action)).isTrue();
                assertThat(action.invoke()).isEqualTo("final-action");
            });
    }

    @Test
    void doesNotProxyAnUnannotatedFinalController() {
        webContextRunner.withUserConfiguration(UnannotatedFinalControllerConfiguration.class)
            .run(context -> {
                assertThat(context).hasNotFailed();
                UnannotatedFinalController controller = context.getBean(UnannotatedFinalController.class);
                assertThat(AopUtils.isAopProxy(controller)).isFalse();
                assertThat(controller.invoke()).isEqualTo("unannotated-final-controller");
            });
    }

    @Test
    void recordsAnnotatedHandlerOutcomesUsingTrustedMetadataAndMethodOverride() throws Exception {
        webContextRunner.withUserConfiguration(IdentityConfiguration.class)
            .withPropertyValues("abnormal.access.monitor.frontend.enabled=false")
            .run(context -> {
                AnnotatedMonitoringInterceptor interceptor = context.getBean(AnnotatedMonitoringInterceptor.class);
                HandlerMethod handler = new HandlerMethod(new AnnotatedController(),
                    AnnotatedController.class.getMethod("export"));

                MockHttpServletRequest successRequest = new MockHttpServletRequest("GET", "/reports/export");
                successRequest.setRemoteAddr("198.51.100.7");
                successRequest.addHeader("X-Request-Id", "success-1");
                MockHttpServletResponse successResponse = new MockHttpServletResponse();
                interceptor.preHandle(successRequest, successResponse, handler);
                interceptor.afterCompletion(successRequest, successResponse, handler, null);

                MockHttpServletRequest deniedRequest = new MockHttpServletRequest("GET", "/reports/export");
                deniedRequest.addHeader("X-Request-Id", "denied-1");
                MockHttpServletResponse deniedResponse = new MockHttpServletResponse();
                deniedResponse.setStatus(403);
                interceptor.preHandle(deniedRequest, deniedResponse, handler);
                interceptor.afterCompletion(deniedRequest, deniedResponse, handler, null);

                MockHttpServletRequest resolvedDeniedRequest = new MockHttpServletRequest("GET", "/reports/export");
                resolvedDeniedRequest.addHeader("X-Request-Id", "resolved-denied-1");
                MockHttpServletResponse resolvedDeniedResponse = new MockHttpServletResponse();
                resolvedDeniedResponse.setStatus(403);
                interceptor.preHandle(resolvedDeniedRequest, resolvedDeniedResponse, handler);
                interceptor.afterCompletion(resolvedDeniedRequest, resolvedDeniedResponse, handler,
                    new SecurityException("resolved access denial"));

                MockHttpServletRequest failureRequest = new MockHttpServletRequest("GET", "/reports/export");
                failureRequest.addHeader("X-Request-Id", "failure-1");
                MockHttpServletResponse failureResponse = new MockHttpServletResponse();
                failureResponse.setStatus(500);
                interceptor.preHandle(failureRequest, failureResponse, handler);
                interceptor.afterCompletion(failureRequest, failureResponse, handler,
                    new IllegalStateException("do not persist this message"));

                List<SecurityEvent> events = ((InMemoryMonitoringRepository) context.getBean(MonitoringRepository.class)).getEvents();
                assertThat(events).hasSize(4);
                assertThat(events.get(0).getAction()).isEqualTo("export-report");
                assertThat(events.get(0).getEventType()).isEqualTo(SecurityEventType.EXPORT);
                assertThat(events.get(0).getResourceType()).isEqualTo("report");
                assertThat(events.get(0).getUserId()).isEqualTo("alice");
                assertThat(events.get(0).getSourceIp()).isEqualTo("198.51.100.7");
                assertThat(events.get(0).getResult()).isEqualTo(SecurityEventResult.SUCCESS);
                assertThat(events.get(1).getResult()).isEqualTo(SecurityEventResult.DENIED);
                assertThat(events.get(1).getReasonCode()).isEqualTo("HTTP_403");
                assertThat(events.get(2).getResult()).isEqualTo(SecurityEventResult.FAILURE);
                assertThat(events.get(2).getReasonCode()).isEqualTo("HANDLER_EXCEPTION");
                assertThat(events.get(3).getResult()).isEqualTo(SecurityEventResult.FAILURE);
                assertThat(events.get(3).getReasonCode()).isEqualTo("HANDLER_EXCEPTION");
            });
    }

    @Test
    void discoversAnnotatedControlTriggerForEnforceMode() {
        AnnotatedTriggerTarget.reset();
        contextRunner.withUserConfiguration(AnnotatedTriggerConfiguration.class)
            .withPropertyValues("abnormal.access.monitor.mode=ENFORCE")
            .run(context -> {
                assertThat(AnnotatedTriggerTarget.isCreated()).isFalse();
                MonitoringOutcome outcome = context.getBean(DefaultSecurityMonitor.class).record(disabledLoginFailure());
                AnnotatedTriggerTarget target = context.getBean(AnnotatedTriggerTarget.class);

                assertThat(outcome.getControls()).hasSize(1);
                assertThat(AnnotatedTriggerTarget.isCreated()).isTrue();
                assertThat(target.getMonitor()).isSameAs(context.getBean(SecurityMonitor.class));
                assertThat(target.getLastCommand()).isNotNull();
                assertThat(target.getLastCommand().getAction()).isEqualTo(ControlActionType.DENY);
            });
    }

    @Test
    void rejectsDuplicateAnnotatedControlTriggers() {
        contextRunner.withUserConfiguration(DuplicateAnnotatedTriggerConfiguration.class)
            .run(context -> assertThat(context).hasFailed());
    }

    private static Class<?> autoConfiguration() {
        try {
            return Class.forName("io.github.jasper.monitoring.spring2.autoconfigure.AbnormalAccessMonitorAutoConfiguration");
        } catch (ClassNotFoundException exception) {
            throw new AssertionError("Spring Boot 2 auto-configuration is missing", exception);
        }
    }

    private static String[] validIpControlProperties() {
        return new String[] {
            "abnormal.access.monitor.ip-control.enabled=true",
            "abnormal.access.monitor.ip-control.protected-paths=/api/**",
            "abnormal.access.monitor.ip-control.excluded-paths=/api/health",
            "abnormal.access.monitor.ip-control.rule-ids=AUTH-02",
            "abnormal.access.monitor.ip-control.permits-per-window=2",
            "abnormal.access.monitor.ip-control.window=1m",
            "abnormal.access.monitor.ip-control.max-ttl=5m",
            "abnormal.access.monitor.ip-control.capacity=100"
        };
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
    static class IdentityConfiguration {
        @Bean
        IdentityContextProvider identityContextProvider() {
            return request -> new IdentityContext("alice", AccountType.PERSON, Collections.singleton("REPORTING"), "session-hash");
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class FinalActionConfiguration {
        @Bean
        FinalAction finalAction() {
            return new FinalActionImplementation();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class UnannotatedFinalControllerConfiguration {
        @Bean
        UnannotatedFinalController unannotatedFinalController() {
            return new UnannotatedFinalController();
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
                public Optional<io.github.jasper.monitoring.core.domain.RuleMatch> evaluate(SecurityEvent event,
                                                                                      List<SecurityEvent> history) {
                    return Optional.empty();
                }
            });
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class EventEnricherConfiguration {
        @Bean
        EventEnricher eventEnricher() {
            return (draft, request, identity) -> draft.toBuilder().orgScope("org-enriched").build();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class AnnotatedTriggerConfiguration {
        @Bean
        @Lazy
        AnnotatedTriggerTarget annotatedTriggerTarget(SecurityMonitor monitor) {
            return new AnnotatedTriggerTarget(monitor);
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class DuplicateAnnotatedTriggerConfiguration {
        @Bean
        DuplicateAnnotatedTriggerTarget firstAnnotatedTriggerTarget() {
            return new DuplicateAnnotatedTriggerTarget();
        }

        @Bean
        DuplicateAnnotatedTriggerTarget secondAnnotatedTriggerTarget() {
            return new DuplicateAnnotatedTriggerTarget();
        }
    }

    @MonitorAction(eventType = SecurityEventType.QUERY, action = "type-default")
    public static class AnnotatedController {
        @MonitorAction(eventType = SecurityEventType.EXPORT, action = "export-report", resourceType = "report")
        public void export() {
        }
    }

    public static class AnnotatedTriggerTarget {
        private static final AtomicBoolean CREATED = new AtomicBoolean();
        private final SecurityMonitor monitor;
        private ControlCommand lastCommand;

        public AnnotatedTriggerTarget(SecurityMonitor monitor) {
            CREATED.set(true);
            this.monitor = monitor;
        }

        static void reset() {
            CREATED.set(false);
        }

        static boolean isCreated() {
            return CREATED.get();
        }

        @ControlTrigger(ControlActionType.DENY)
        public void deny(ControlCommand command) {
            lastCommand = command;
        }

        ControlCommand getLastCommand() {
            return lastCommand;
        }

        SecurityMonitor getMonitor() {
            return monitor;
        }
    }

    public static class DuplicateAnnotatedTriggerTarget {
        @ControlTrigger(ControlActionType.DENY)
        public void deny(ControlCommand command) {
        }
    }

    interface FinalAction {
        @MonitorAction(action = "final-interface-action")
        String invoke();
    }

    static final class FinalActionImplementation implements FinalAction {
        @Override
        @MonitorAction(action = "final-implementation-action")
        public String invoke() {
            return "final-action";
        }
    }

    @org.springframework.stereotype.Controller
    static final class UnannotatedFinalController {
        String invoke() {
            return "unannotated-final-controller";
        }
    }
}

package io.github.jasper.monitoring.spring3.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.jasper.monitoring.api.AccountType;
import io.github.jasper.monitoring.api.IdentityContext;
import io.github.jasper.monitoring.api.MonitorAction;
import io.github.jasper.monitoring.api.MonitorActionAttribute;
import io.github.jasper.monitoring.api.MonitorActionEnricher;
import io.github.jasper.monitoring.api.MonitorActionFacts;
import io.github.jasper.monitoring.api.MonitorActionInvocation;
import io.github.jasper.monitoring.api.SecurityEventDraft;
import io.github.jasper.monitoring.api.SecurityEventResult;
import io.github.jasper.monitoring.api.SecurityEventType;
import io.github.jasper.monitoring.core.application.ActionEventRecorder;
import io.github.jasper.monitoring.core.application.MonitoringOutcome;
import io.github.jasper.monitoring.core.application.SecurityMonitor;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.method.HandlerMethod;

class DynamicActionFactsTest {
    private final WebApplicationContextRunner webContextRunner = new WebApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(AbnormalAccessMonitorAutoConfiguration.class));

    @AfterEach
    void clearsRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void registersTheActionAspectForMvcInstrumentation() {
        webContextRunner.run(context -> assertThat(context).hasSingleBean(AnnotatedActionMonitoringAspect.class));
    }

    @Test
    void capturesNestedParameterFactsAndReturningEnricherFacts() throws Exception {
        Fixture fixture = new Fixture();
        MockHttpServletRequest request = fixture.request("/reports/export");
        MockHttpServletResponse response = new MockHttpServletResponse();
        HandlerMethod handler = fixture.handler("export", ExportRequest.class);

        fixture.interceptor.preHandle(request, response, handler);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        assertThat(fixture.controller.export(new ExportRequest("report-42", "org-a", "internal"))).isEqualTo("exported");
        fixture.interceptor.afterCompletion(request, response, handler, null);

        SecurityEventDraft event = fixture.onlyEvent();
        assertThat(event.getResourceId()).isEqualTo("report-42");
        assertThat(event.getOrgScope()).isEqualTo("org-a");
        assertThat(event.getAttribute("classification")).isEqualTo("static");
        assertThat(event.getAttribute("channel")).isEqualTo("internal");
        assertThat(event.getDataCount()).isEqualTo(7L);
        assertThat(event.getResult()).isEqualTo(SecurityEventResult.SUCCESS);
        assertThat(event.getReasonCode()).isEqualTo("BUSINESS_OK");
    }

    @Test
    void letsHttpForbiddenOverrideBusinessSuccess() throws Exception {
        Fixture fixture = new Fixture();
        MockHttpServletRequest request = fixture.request("/reports/export");
        MockHttpServletResponse response = new MockHttpServletResponse();
        HandlerMethod handler = fixture.handler("export", ExportRequest.class);

        fixture.interceptor.preHandle(request, response, handler);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        fixture.controller.export(new ExportRequest("report-42", "org-a", "internal"));
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        fixture.interceptor.afterCompletion(request, response, handler, null);

        SecurityEventDraft event = fixture.onlyEvent();
        assertThat(event.getResult()).isEqualTo(SecurityEventResult.DENIED);
        assertThat(event.getReasonCode()).isEqualTo("HTTP_403");
    }

    @Test
    void ignoresInvalidAndNullParameterPathsWithoutAffectingTheResponse() throws Exception {
        Fixture fixture = new Fixture();
        MockHttpServletRequest request = fixture.request("/reports/invalid");
        MockHttpServletResponse response = new MockHttpServletResponse();
        HandlerMethod handler = fixture.handler("invalid", ExportRequest.class, ExportRequest.class,
            ExportRequest.class);

        fixture.interceptor.preHandle(request, response, handler);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        assertThat(fixture.controller.invalid(new ExportRequest("report-42", "org-a", "internal"),
            new ExportRequest("report-42", "org-a", "internal"), null)).isEqualTo("still-running");
        fixture.interceptor.afterCompletion(request, response, handler, null);

        SecurityEventDraft event = fixture.onlyEvent();
        assertThat(response.getStatus()).isLessThan(400);
        assertThat(event.getResourceId()).isNull();
        assertThat(event.getOrgScope()).isNull();
        assertThat(event.getAttribute("missing")).isNull();
    }

    private static final class Fixture {
        private final CapturingSecurityMonitor monitor = new CapturingSecurityMonitor();
        private final AnnotatedActionMonitoringInterceptor interceptor = new AnnotatedActionMonitoringInterceptor(
            new ActionEventRecorder(monitor, Clock.systemUTC()), (directAddress, forwardedFor) -> directAddress,
            request -> new IdentityContext("operator", AccountType.PERSON, null, null));
        private final FactsController controller;

        private Fixture() {
            DefaultListableBeanFactory factory = new DefaultListableBeanFactory();
            factory.registerSingleton("returningFactsEnricher", new ReturningFactsEnricher());
            factory.registerSingleton("failingFactsEnricher", new FailingFactsEnricher());
            AspectJProxyFactory proxyFactory = new AspectJProxyFactory(new FactsController());
            proxyFactory.setProxyTargetClass(true);
            proxyFactory.addAspect(new AnnotatedActionMonitoringAspect(factory));
            controller = proxyFactory.getProxy();
        }

        private MockHttpServletRequest request(String path) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
            request.setRemoteAddr("198.51.100.7");
            request.addHeader("X-Request-Id", "request-42");
            return request;
        }

        private HandlerMethod handler(String methodName, Class<?>... types) throws Exception {
            return new HandlerMethod(controller, FactsController.class.getMethod(methodName, types));
        }

        private SecurityEventDraft onlyEvent() {
            assertThat(monitor.events).hasSize(1);
            return monitor.events.get(0);
        }
    }

    public static class FactsController {
        @MonitorAction(eventType = SecurityEventType.EXPORT, action = "dynamic-export", resourceType = "report",
            enrichers = {ReturningFactsEnricher.class, FailingFactsEnricher.class})
        @MonitorActionAttribute(name = "classification", value = "static")
        public String export(
            @MonitorActionAttribute(target = io.github.jasper.monitoring.api.MonitorActionAttributeTarget.RESOURCE_ID,
                path = "resource.id")
            @MonitorActionAttribute(target = io.github.jasper.monitoring.api.MonitorActionAttributeTarget.ORG_SCOPE,
                path = "organization.id")
            @MonitorActionAttribute(name = "channel", path = "channels[0].name") ExportRequest request) {
            return "exported";
        }

        @MonitorAction(action = "invalid-path")
        public String invalid(
            @MonitorActionAttribute(target = io.github.jasper.monitoring.api.MonitorActionAttributeTarget.RESOURCE_ID,
                path = "resource.class") ExportRequest invalid,
            @MonitorActionAttribute(name = "missing", path = "channels[9].name") ExportRequest outOfRange,
            @MonitorActionAttribute(target = io.github.jasper.monitoring.api.MonitorActionAttributeTarget.ORG_SCOPE,
                path = "organization.id") ExportRequest nullValue) {
            return "still-running";
        }
    }

    public static final class ReturningFactsEnricher implements MonitorActionEnricher {
        @Override
        public MonitorActionFacts enrich(MonitorActionInvocation invocation) {
            if (invocation.getPhase() != MonitorActionInvocation.Phase.AFTER_RETURNING) {
                return MonitorActionFacts.empty();
            }
            return MonitorActionFacts.builder().dataCount(7).result(SecurityEventResult.SUCCESS)
                .reasonCode("BUSINESS_OK").attribute("classification", "dynamic").build();
        }
    }

    public static final class FailingFactsEnricher implements MonitorActionEnricher {
        @Override
        public MonitorActionFacts enrich(MonitorActionInvocation invocation) {
            throw new IllegalStateException("ignored");
        }
    }

    private static final class ExportRequest {
        private final Resource resource;
        private final Organization organization;
        private final List<Channel> channels;

        private ExportRequest(String resourceId, String organizationId, String channel) {
            this.resource = new Resource(resourceId);
            this.organization = new Organization(organizationId);
            this.channels = java.util.Collections.singletonList(new Channel(channel));
        }

        public Resource getResource() {
            return resource;
        }

        public Organization getOrganization() {
            return organization;
        }

        public List<Channel> getChannels() {
            return channels;
        }
    }

    private static final class Resource {
        private final String id;

        private Resource(String id) {
            this.id = id;
        }

        public String getId() {
            return id;
        }
    }

    private static final class Organization {
        private final String id;

        private Organization(String id) {
            this.id = id;
        }

        public String getId() {
            return id;
        }
    }

    private static final class Channel {
        private final String name;

        private Channel(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }

    private static final class CapturingSecurityMonitor implements SecurityMonitor {
        private final List<SecurityEventDraft> events = new ArrayList<SecurityEventDraft>();

        @Override
        public MonitoringOutcome record(SecurityEventDraft draft) {
            events.add(draft);
            return null;
        }
    }
}

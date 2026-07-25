package io.github.jasper.monitoring.spring3.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.jasper.monitoring.api.AccountType;
import io.github.jasper.monitoring.api.EventInputValidation;
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
import io.github.jasper.monitoring.spring.support.AnnotatedActionFacts;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

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
        assertThat(fixture.monitor.validations).singleElement().satisfies(validation ->
            assertThat(validation.getIssues()).extracting(issue -> issue.getIssueCode())
                .contains("PROTECTED_FACT_OVERRIDE"));
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
    void keepsRuleTagsStaticWhenParametersAndEnrichersContributeRuleTagKeys() throws Exception {
        Fixture fixture = new Fixture();
        MockHttpServletRequest request = fixture.request("/reports/tagged");
        MockHttpServletResponse response = new MockHttpServletResponse();
        HandlerMethod handler = fixture.handler("tagged", String.class, String.class);

        fixture.interceptor.preHandle(request, response, handler);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        assertThat(fixture.controller.tagged("false", "false")).isEqualTo("tagged");
        fixture.interceptor.afterCompletion(request, response, handler, null);

        SecurityEventDraft event = fixture.onlyEvent();
        assertThat(event.getAttribute("monitor.rule-tag.approved")).isEqualTo("true");
        assertThat(event.getAttribute("monitor.rule-tag.injected")).isNull();
    }

    @Test
    void ignoresAnUnrelatedControllerWithTheSameMethodSignature() throws Exception {
        Fixture fixture = new Fixture();
        CountingFactsEnricher.reset();
        MockHttpServletRequest request = fixture.request("/reports/primary");
        MockHttpServletResponse response = new MockHttpServletResponse();
        HandlerMethod handler = fixture.handler("same", String.class);

        fixture.interceptor.preHandle(request, response, handler);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        assertThat(fixture.unrelatedController.same("resource-from-b")).isEqualTo("unrelated");
        fixture.interceptor.afterCompletion(request, response, handler, null);

        SecurityEventDraft event = fixture.onlyEvent();
        assertThat(event.getResourceId()).isNull();
        assertThat(CountingFactsEnricher.invocations.get()).isZero();
    }

    @Test
    void skipsDynamicCollectionForCallableAndStreamingMvcHandlers() throws Exception {
        Fixture fixture = new Fixture();
        CountingFactsEnricher.reset();

        MockHttpServletRequest callableRequest = fixture.request("/reports/callable");
        MockHttpServletResponse callableResponse = new MockHttpServletResponse();
        HandlerMethod callableHandler = fixture.handler("callable", String.class);
        fixture.interceptor.preHandle(callableRequest, callableResponse, callableHandler);
        assertThat(callableRequest.getAttribute(io.github.jasper.monitoring.spring.support.AnnotatedActionFacts.REQUEST_ATTRIBUTE))
            .isNull();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(callableRequest));
        assertThat(fixture.controller.callable("report-42").call()).isEqualTo("deferred");
        fixture.interceptor.afterCompletion(callableRequest, callableResponse, callableHandler, null);

        MockHttpServletRequest streamingRequest = fixture.request("/reports/stream");
        MockHttpServletResponse streamingResponse = new MockHttpServletResponse();
        HandlerMethod streamingHandler = fixture.handler("stream", String.class);
        fixture.interceptor.preHandle(streamingRequest, streamingResponse, streamingHandler);
        assertThat(streamingRequest.getAttribute(io.github.jasper.monitoring.spring.support.AnnotatedActionFacts.REQUEST_ATTRIBUTE))
            .isNull();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(streamingRequest));
        assertThat(fixture.controller.stream("report-42")).isNotNull();
        fixture.interceptor.afterCompletion(streamingRequest, streamingResponse, streamingHandler, null);

        assertThat(CountingFactsEnricher.invocations.get()).isZero();
        assertThat(fixture.monitor.events).isEmpty();
    }

    @Test
    void skipsDynamicCollectionForWrappedStreamingMvcHandlers() throws Exception {
        Fixture fixture = new Fixture();
        CountingFactsEnricher.reset();

        MockHttpServletRequest streamRequest = fixture.request("/reports/wrapped-stream");
        MockHttpServletResponse streamResponse = new MockHttpServletResponse();
        HandlerMethod streamHandler = fixture.handler("wrappedStream", String.class);
        fixture.interceptor.preHandle(streamRequest, streamResponse, streamHandler);
        assertThat(streamRequest.getAttribute(
            io.github.jasper.monitoring.spring.support.AnnotatedActionFacts.REQUEST_ATTRIBUTE)).isNull();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(streamRequest));
        assertThat(fixture.controller.wrappedStream("report-42").getBody()).isNotNull();
        fixture.interceptor.afterCompletion(streamRequest, streamResponse, streamHandler, null);

        MockHttpServletRequest emitterRequest = fixture.request("/reports/wrapped-emitter");
        MockHttpServletResponse emitterResponse = new MockHttpServletResponse();
        HandlerMethod emitterHandler = fixture.handler("wrappedEmitter", String.class);
        fixture.interceptor.preHandle(emitterRequest, emitterResponse, emitterHandler);
        assertThat(emitterRequest.getAttribute(
            io.github.jasper.monitoring.spring.support.AnnotatedActionFacts.REQUEST_ATTRIBUTE)).isNull();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(emitterRequest));
        assertThat(fixture.controller.wrappedEmitter("report-42").getBody()).isNotNull();
        fixture.interceptor.afterCompletion(emitterRequest, emitterResponse, emitterHandler, null);

        assertThat(CountingFactsEnricher.invocations.get()).isZero();
        assertThat(fixture.monitor.events).isEmpty();
    }

    @Test
    void skipsDynamicCollectionForLowerBoundWrappedStreamingHandler() throws Exception {
        Fixture fixture = new Fixture();
        CountingFactsEnricher.reset();
        MockHttpServletRequest request = fixture.request("/reports/lower-bound-stream");
        MockHttpServletResponse response = new MockHttpServletResponse();
        HandlerMethod handler = fixture.handler("lowerBoundStream", String.class);

        fixture.interceptor.preHandle(request, response, handler);
        assertThat(request.getAttribute(
            io.github.jasper.monitoring.spring.support.AnnotatedActionFacts.REQUEST_ATTRIBUTE)).isNull();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        assertThat(fixture.controller.lowerBoundStream("report-42").getBody())
            .isInstanceOf(StreamingResponseBody.class);
        fixture.interceptor.afterCompletion(request, response, handler, null);

        assertThat(CountingFactsEnricher.invocations.get()).isZero();
        assertThat(fixture.monitor.events).isEmpty();
    }

    @Test
    void skipsDynamicCollectionForBoundedGenericStreamingHandler() throws Exception {
        Fixture fixture = new Fixture();
        CountingFactsEnricher.reset();
        MockHttpServletRequest request = fixture.request("/reports/bounded-stream");
        MockHttpServletResponse response = new MockHttpServletResponse();
        HandlerMethod handler = fixture.handler("boundedStream", String.class);

        fixture.interceptor.preHandle(request, response, handler);
        assertThat(request.getAttribute(
            io.github.jasper.monitoring.spring.support.AnnotatedActionFacts.REQUEST_ATTRIBUTE)).isNull();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        assertThat(fixture.controller.<StreamingResponseBody>boundedStream("report-42")).isNotNull();
        fixture.interceptor.afterCompletion(request, response, handler, null);

        assertThat(CountingFactsEnricher.invocations.get()).isZero();
        assertThat(fixture.monitor.events).isEmpty();
    }

    @Test
    void skipsDynamicCollectionForGenericArrayAndInheritedStreamingHandlers() throws Exception {
        Fixture fixture = new Fixture();
        CountingFactsEnricher.reset();

        MockHttpServletRequest arrayRequest = fixture.request("/reports/bounded-stream-array");
        MockHttpServletResponse arrayResponse = new MockHttpServletResponse();
        HandlerMethod arrayHandler = fixture.handler("boundedStreamArray", String.class);
        fixture.interceptor.preHandle(arrayRequest, arrayResponse, arrayHandler);
        assertThat(arrayRequest.getAttribute(
            io.github.jasper.monitoring.spring.support.AnnotatedActionFacts.REQUEST_ATTRIBUTE)).isNull();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(arrayRequest));
        assertThat(fixture.controller.<StreamingResponseBody>boundedStreamArray("report-42")).hasSize(1);
        fixture.interceptor.afterCompletion(arrayRequest, arrayResponse, arrayHandler, null);

        MockHttpServletRequest inheritedRequest = fixture.request("/reports/inherited-stream");
        MockHttpServletResponse inheritedResponse = new MockHttpServletResponse();
        HandlerMethod inheritedHandler = fixture.handler("inheritedStream", String.class);
        fixture.interceptor.preHandle(inheritedRequest, inheritedResponse, inheritedHandler);
        assertThat(inheritedRequest.getAttribute(
            io.github.jasper.monitoring.spring.support.AnnotatedActionFacts.REQUEST_ATTRIBUTE)).isNull();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(inheritedRequest));
        assertThat(fixture.controller.inheritedStream("report-42").getBody()).isNotNull();
        fixture.interceptor.afterCompletion(inheritedRequest, inheritedResponse, inheritedHandler, null);

        assertThat(CountingFactsEnricher.invocations.get()).isZero();
        assertThat(fixture.monitor.events).isEmpty();
    }

    @Test
    void skipsDynamicCollectionForConcreteStreamingArrayHandler() throws Exception {
        Fixture fixture = new Fixture();
        CountingFactsEnricher.reset();
        MockHttpServletRequest request = fixture.request("/reports/stream-array");
        MockHttpServletResponse response = new MockHttpServletResponse();
        HandlerMethod handler = fixture.handler("streamArray", String.class);

        fixture.interceptor.preHandle(request, response, handler);
        assertThat(request.getAttribute(
            io.github.jasper.monitoring.spring.support.AnnotatedActionFacts.REQUEST_ATTRIBUTE)).isNull();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        assertThat(fixture.controller.streamArray("report-42")).hasSize(1);
        fixture.interceptor.afterCompletion(request, response, handler, null);

        assertThat(CountingFactsEnricher.invocations.get()).isZero();
        assertThat(fixture.monitor.events).isEmpty();
    }

    @Test
    void skipsDynamicCollectionForUnresolvedGenericMvcHandlers() throws Exception {
        Fixture fixture = new Fixture();
        CountingFactsEnricher.reset();

        MockHttpServletRequest typeVariableRequest = fixture.request("/reports/unresolved-type-variable");
        MockHttpServletResponse typeVariableResponse = new MockHttpServletResponse();
        HandlerMethod typeVariableHandler = fixture.handler("unresolvedTypeVariable", String.class);
        fixture.interceptor.preHandle(typeVariableRequest, typeVariableResponse, typeVariableHandler);
        assertThat(typeVariableRequest.getAttribute(
            io.github.jasper.monitoring.spring.support.AnnotatedActionFacts.REQUEST_ATTRIBUTE)).isNull();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(typeVariableRequest));
        Object typeVariableResult = fixture.controller.unresolvedTypeVariable("report-42");
        assertThat(typeVariableResult).isInstanceOf(StreamingResponseBody.class);
        fixture.interceptor.afterCompletion(typeVariableRequest, typeVariableResponse, typeVariableHandler, null);

        MockHttpServletRequest wildcardRequest = fixture.request("/reports/unbounded-wildcard");
        MockHttpServletResponse wildcardResponse = new MockHttpServletResponse();
        HandlerMethod wildcardHandler = fixture.handler("unboundedWildcard", String.class);
        fixture.interceptor.preHandle(wildcardRequest, wildcardResponse, wildcardHandler);
        assertThat(wildcardRequest.getAttribute(
            io.github.jasper.monitoring.spring.support.AnnotatedActionFacts.REQUEST_ATTRIBUTE)).isNull();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(wildcardRequest));
        assertThat(fixture.controller.unboundedWildcard("report-42").getBody())
            .isInstanceOf(StreamingResponseBody.class);
        fixture.interceptor.afterCompletion(wildcardRequest, wildcardResponse, wildcardHandler, null);

        MockHttpServletRequest httpEntityRequest = fixture.request("/reports/unresolved-http-entity");
        MockHttpServletResponse httpEntityResponse = new MockHttpServletResponse();
        HandlerMethod httpEntityHandler = fixture.handler("unresolvedHttpEntity", String.class);
        fixture.interceptor.preHandle(httpEntityRequest, httpEntityResponse, httpEntityHandler);
        assertThat(httpEntityRequest.getAttribute(
            io.github.jasper.monitoring.spring.support.AnnotatedActionFacts.REQUEST_ATTRIBUTE)).isNull();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(httpEntityRequest));
        assertThat(fixture.controller.<Object>unresolvedHttpEntity("report-42").getBody())
            .isInstanceOf(StreamingResponseBody.class);
        fixture.interceptor.afterCompletion(httpEntityRequest, httpEntityResponse, httpEntityHandler, null);

        MockHttpServletRequest rawHttpEntityRequest = fixture.request("/reports/raw-http-entity");
        MockHttpServletResponse rawHttpEntityResponse = new MockHttpServletResponse();
        HandlerMethod rawHttpEntityHandler = fixture.handler("rawHttpEntity", String.class);
        fixture.interceptor.preHandle(rawHttpEntityRequest, rawHttpEntityResponse, rawHttpEntityHandler);
        assertThat(rawHttpEntityRequest.getAttribute(
            io.github.jasper.monitoring.spring.support.AnnotatedActionFacts.REQUEST_ATTRIBUTE)).isNull();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(rawHttpEntityRequest));
        assertThat(fixture.controller.rawHttpEntity("report-42").getBody())
            .isInstanceOf(StreamingResponseBody.class);
        fixture.interceptor.afterCompletion(rawHttpEntityRequest, rawHttpEntityResponse, rawHttpEntityHandler, null);

        MockHttpServletRequest rawSafeResponseRequest = fixture.request("/reports/raw-safe-response");
        MockHttpServletResponse rawSafeResponseResponse = new MockHttpServletResponse();
        HandlerMethod rawSafeResponseHandler = fixture.handler("rawSafeResponse", String.class);
        fixture.interceptor.preHandle(rawSafeResponseRequest, rawSafeResponseResponse, rawSafeResponseHandler);
        assertThat(rawSafeResponseRequest.getAttribute(
            io.github.jasper.monitoring.spring.support.AnnotatedActionFacts.REQUEST_ATTRIBUTE)).isNull();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(rawSafeResponseRequest));
        assertThat(fixture.controller.rawSafeResponse("report-42").getBody()).isEqualTo("safe-response");
        fixture.interceptor.afterCompletion(rawSafeResponseRequest, rawSafeResponseResponse, rawSafeResponseHandler, null);

        assertThat(CountingFactsEnricher.invocations.get()).isZero();
        assertThat(fixture.monitor.events).isEmpty();
    }

    @Test
    void capturesFactsForKnownSynchronousGenericHttpEntityHandler() throws Exception {
        Fixture fixture = new Fixture();
        CountingFactsEnricher.reset();
        MockHttpServletRequest request = fixture.request("/reports/safe-response");
        MockHttpServletResponse response = new MockHttpServletResponse();
        HandlerMethod handler = fixture.handler("safeResponse", String.class);

        fixture.interceptor.preHandle(request, response, handler);
        assertThat(request.getAttribute(
            io.github.jasper.monitoring.spring.support.AnnotatedActionFacts.REQUEST_ATTRIBUTE)).isNotNull();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        assertThat(fixture.controller.safeResponse("report-42").getBody()).isEqualTo("safe-response");
        fixture.interceptor.afterCompletion(request, response, handler, null);

        SecurityEventDraft event = fixture.onlyEvent();
        assertThat(event.getAction()).isEqualTo("safe-response");
        assertThat(event.getResourceId()).isEqualTo("report-42");
        assertThat(CountingFactsEnricher.invocations.get()).isEqualTo(2);
    }

    @Test
    void capturesFactsForResolvedNonFinalHttpEntityPayload() throws Exception {
        Fixture fixture = new Fixture();
        CountingFactsEnricher.reset();
        MockHttpServletRequest request = fixture.request("/reports/map-response");
        MockHttpServletResponse response = new MockHttpServletResponse();
        HandlerMethod handler = fixture.handler("mapResponse", String.class);

        fixture.interceptor.preHandle(request, response, handler);
        assertThat(request.getAttribute(AnnotatedActionFacts.REQUEST_ATTRIBUTE)).isNotNull();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        assertThat(fixture.controller.mapResponse("report-42").getBody()).containsEntry("id", "report-42");
        fixture.interceptor.afterCompletion(request, response, handler, null);

        SecurityEventDraft event = fixture.onlyEvent();
        assertThat(event.getAction()).isEqualTo("map-response");
        assertThat(event.getResourceId()).isEqualTo("report-42");
        assertThat(CountingFactsEnricher.invocations.get()).isEqualTo(2);
    }

    @Test
    void resolvesInterfaceMethodActionFromClassBasedMvcProxy() throws Exception {
        Fixture fixture = new Fixture();
        CountingFactsEnricher.reset();
        MockHttpServletRequest request = fixture.request("/reports/interface-method");
        MockHttpServletResponse response = new MockHttpServletResponse();
        HandlerMethod handler = fixture.interfaceMethodHandler();

        fixture.interceptor.preHandle(request, response, handler);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        assertThat(fixture.interfaceMethodController.export("report-42", "org-a")).isEqualTo("interface-method");
        fixture.interceptor.afterCompletion(request, response, handler, null);

        SecurityEventDraft event = fixture.onlyEvent();
        assertThat(event.getAction()).isEqualTo("interface-method");
        assertThat(event.getResourceType()).isEqualTo("report");
        assertThat(event.getResourceId()).isEqualTo("report-42");
        assertThat(event.getOrgScope()).isEqualTo("org-a");
        assertThat(event.getAttribute("method-source")).isEqualTo("method");
        assertThat(event.getAttribute("type-source")).isNull();
        assertThat(event.getDataCount()).isEqualTo(7L);
        assertThat(CountingFactsEnricher.invocations.get()).isZero();
    }

    @Test
    void resolvesGenericInterfaceMethodActionFromSpecializedClassBasedMvcProxy() throws Exception {
        Fixture fixture = new Fixture();
        MockHttpServletRequest request = fixture.request("/reports/generic-interface-method");
        MockHttpServletResponse response = new MockHttpServletResponse();
        HandlerMethod handler = fixture.genericInterfaceMethodHandler();

        fixture.interceptor.preHandle(request, response, handler);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        assertThat(fixture.genericInterfaceMethodController.export("report-42", "org-a"))
            .isEqualTo("generic-interface-method");
        fixture.interceptor.afterCompletion(request, response, handler, null);

        SecurityEventDraft event = fixture.onlyEvent();
        assertThat(event.getAction()).isEqualTo("generic-interface-method");
        assertThat(event.getResourceId()).isEqualTo("report-42");
        assertThat(event.getOrgScope()).isEqualTo("org-a");
    }

    @Test
    void doesNotResolveGenericInterfaceActionForUnrelatedOverload() throws Exception {
        Fixture fixture = new Fixture();
        MockHttpServletRequest request = fixture.request("/reports/generic-interface-unrelated");
        MockHttpServletResponse response = new MockHttpServletResponse();
        HandlerMethod handler = fixture.genericInterfaceUnrelatedHandler();

        fixture.interceptor.preHandle(request, response, handler);
        assertThat(request.getAttribute(AnnotatedActionFacts.REQUEST_ATTRIBUTE)).isNull();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        assertThat(fixture.genericInterfaceMethodController.export((CharSequence) "report-42", (CharSequence) "org-a"))
            .isEqualTo("generic-interface-unrelated");
        fixture.interceptor.afterCompletion(request, response, handler, null);

        assertThat(fixture.monitor.events).isEmpty();
    }

    @Test
    void resolvesInterfaceTypeActionFromJdkMvcProxy() throws Exception {
        Fixture fixture = new Fixture();
        MockHttpServletRequest request = fixture.request("/reports/interface-type");
        MockHttpServletResponse response = new MockHttpServletResponse();
        HandlerMethod handler = fixture.interfaceTypeHandler();

        fixture.interceptor.preHandle(request, response, handler);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        assertThat(fixture.interfaceTypeController.export("tenant-42", "org-a")).isEqualTo("interface-type");
        fixture.interceptor.afterCompletion(request, response, handler, null);

        SecurityEventDraft event = fixture.onlyEvent();
        assertThat(event.getAction()).isEqualTo("interface-type");
        assertThat(event.getResourceType()).isEqualTo("tenant");
        assertThat(event.getResourceId()).isEqualTo("tenant-42");
        assertThat(event.getOrgScope()).isEqualTo("org-a");
        assertThat(event.getAttribute("type-source")).isEqualTo("type");
        assertThat(event.getAttribute("implementation-source")).isNull();
        assertThat(event.getDataCount()).isEqualTo(7L);
    }

    @Test
    void ignoresInvalidAndNullParameterPathsWithoutAffectingTheResponse() throws Exception {
        Fixture fixture = new Fixture();
        MockHttpServletRequest request = fixture.request("/reports/invalid");
        MockHttpServletResponse response = new MockHttpServletResponse();
        HandlerMethod handler = fixture.handler("invalid", ExportRequest.class, ExportRequest.class,
            ExportRequest.class, ExplodingPayload.class, String.class, ExportRequest.class);

        fixture.interceptor.preHandle(request, response, handler);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        assertThat(fixture.controller.invalid(new ExportRequest("report-42", "org-a", "internal"),
            new ExportRequest("report-42", "org-a", "internal"), null, new ExplodingPayload(), "unsafe",
            new ExportRequest("report-42", "org-a", "internal")))
            .isEqualTo("still-running");
        fixture.interceptor.afterCompletion(request, response, handler, null);

        SecurityEventDraft event = fixture.onlyEvent();
        assertThat(response.getStatus()).isLessThan(400);
        assertThat(event.getResourceId()).isNull();
        assertThat(event.getOrgScope()).isNull();
        assertThat(event.getAttribute("missing")).isNull();
        assertThat(event.getAttribute("broken")).isNull();
        assertThat(event.getAttribute("password")).isNull();
        assertThat(event.getAttribute("leading")).isNull();
        assertThat(fixture.monitor.validations).singleElement().satisfies(validation ->
            assertThat(validation.getIssues()).extracting(issue -> issue.getIssueCode())
                .contains("UNRESOLVED_PARAMETER_PATH", "INVALID_PARAMETER_VALUE"));
    }

    private static final class Fixture {
        private final CapturingSecurityMonitor monitor = new CapturingSecurityMonitor();
        private final AnnotatedActionMonitoringInterceptor interceptor = new AnnotatedActionMonitoringInterceptor(
            new ActionEventRecorder(monitor, Clock.systemUTC()), (directAddress, forwardedFor) -> directAddress,
            request -> new IdentityContext("operator", AccountType.PERSON, null, null));
        private final FactsController controller;
        private final UnrelatedController unrelatedController;
        private final InterfaceMethodController interfaceMethodController;
        private final GenericInterfaceMethodController<String> genericInterfaceMethodController;
        private final InterfaceTypeController interfaceTypeController;

        private Fixture() {
            DefaultListableBeanFactory factory = new DefaultListableBeanFactory();
            factory.registerSingleton("returningFactsEnricher", new ReturningFactsEnricher());
            factory.registerSingleton("failingFactsEnricher", new FailingFactsEnricher());
            factory.registerSingleton("tagFactsEnricher", new TagFactsEnricher());
            factory.registerSingleton("countingFactsEnricher", new CountingFactsEnricher());
            AnnotatedActionMonitoringAspect aspect = new AnnotatedActionMonitoringAspect(factory);
            AspectJProxyFactory proxyFactory = new AspectJProxyFactory(new FactsController());
            proxyFactory.setProxyTargetClass(true);
            proxyFactory.addAdvisor(aspect);
            controller = proxyFactory.getProxy();
            AspectJProxyFactory unrelatedProxyFactory = new AspectJProxyFactory(new UnrelatedController());
            unrelatedProxyFactory.setProxyTargetClass(true);
            unrelatedProxyFactory.addAdvisor(aspect);
            unrelatedController = unrelatedProxyFactory.getProxy();
            AspectJProxyFactory interfaceMethodProxyFactory = new AspectJProxyFactory(
                new InterfaceMethodControllerImpl());
            interfaceMethodProxyFactory.setProxyTargetClass(true);
            interfaceMethodProxyFactory.addAdvisor(aspect);
            interfaceMethodController = interfaceMethodProxyFactory.getProxy();
            AspectJProxyFactory genericInterfaceMethodProxyFactory = new AspectJProxyFactory(
                new GenericInterfaceMethodControllerImpl());
            genericInterfaceMethodProxyFactory.setProxyTargetClass(true);
            genericInterfaceMethodProxyFactory.addAdvisor(aspect);
            genericInterfaceMethodController = genericInterfaceMethodProxyFactory.getProxy();
            AspectJProxyFactory interfaceTypeProxyFactory = new AspectJProxyFactory(new InterfaceTypeControllerImpl());
            interfaceTypeProxyFactory.setProxyTargetClass(false);
            interfaceTypeProxyFactory.addAdvisor(aspect);
            interfaceTypeController = interfaceTypeProxyFactory.getProxy();
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

        private HandlerMethod interfaceMethodHandler() throws Exception {
            return new HandlerMethod(interfaceMethodController,
                InterfaceMethodController.class.getMethod("export", String.class, String.class));
        }

        private HandlerMethod interfaceTypeHandler() throws Exception {
            return new HandlerMethod(interfaceTypeController,
                InterfaceTypeController.class.getMethod("export", String.class, String.class));
        }

        private HandlerMethod genericInterfaceMethodHandler() throws Exception {
            return new HandlerMethod(genericInterfaceMethodController,
                GenericInterfaceMethodControllerImpl.class.getMethod("export", String.class, String.class));
        }

        private HandlerMethod genericInterfaceUnrelatedHandler() throws Exception {
            return new HandlerMethod(genericInterfaceMethodController,
                GenericInterfaceMethodControllerImpl.class.getMethod("export", CharSequence.class, CharSequence.class));
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
                path = "organization.id") ExportRequest nullValue,
            @MonitorActionAttribute(name = "broken", path = "value") ExplodingPayload exploding,
            @MonitorActionAttribute(name = "password") String unsafeName,
            @MonitorActionAttribute(name = "leading", path = ".resource.id") ExportRequest leadingPath) {
            return "still-running";
        }

        @MonitorAction(action = "tagged", ruleTags = {"approved"}, enrichers = TagFactsEnricher.class)
        public String tagged(@MonitorActionAttribute(name = "monitor.rule-tag.approved") String existing,
                             @MonitorActionAttribute(name = "monitor.rule-tag.injected") String injected) {
            return "tagged";
        }

        @MonitorAction(action = "callable", enrichers = CountingFactsEnricher.class)
        public Callable<String> callable(
            @MonitorActionAttribute(target = io.github.jasper.monitoring.api.MonitorActionAttributeTarget.RESOURCE_ID)
            String resourceId) {
            return () -> "deferred";
        }

        @MonitorAction(action = "stream", enrichers = CountingFactsEnricher.class)
        public StreamingResponseBody stream(
            @MonitorActionAttribute(target = io.github.jasper.monitoring.api.MonitorActionAttributeTarget.RESOURCE_ID)
            String resourceId) {
            return output -> output.write(resourceId.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }

        @MonitorAction(action = "wrapped-stream", enrichers = CountingFactsEnricher.class)
        public ResponseEntity<StreamingResponseBody> wrappedStream(
            @MonitorActionAttribute(target = io.github.jasper.monitoring.api.MonitorActionAttributeTarget.RESOURCE_ID)
            String resourceId) {
            return ResponseEntity.ok(output ->
                output.write(resourceId.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        }

        @MonitorAction(action = "wrapped-emitter", enrichers = CountingFactsEnricher.class)
        public ResponseEntity<ResponseBodyEmitter> wrappedEmitter(
            @MonitorActionAttribute(target = io.github.jasper.monitoring.api.MonitorActionAttributeTarget.RESOURCE_ID)
            String resourceId) {
            return ResponseEntity.ok(new ResponseBodyEmitter());
        }

        @MonitorAction(action = "lower-bound-stream", enrichers = CountingFactsEnricher.class)
        public ResponseEntity<? super StreamingResponseBody> lowerBoundStream(
            @MonitorActionAttribute(target = io.github.jasper.monitoring.api.MonitorActionAttributeTarget.RESOURCE_ID)
            String resourceId) {
            StreamingResponseBody body = output ->
                output.write(resourceId.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return ResponseEntity.ok((Object) body);
        }

        @MonitorAction(action = "bounded-stream", enrichers = CountingFactsEnricher.class)
        @SuppressWarnings("unchecked")
        public <T extends StreamingResponseBody> T boundedStream(
            @MonitorActionAttribute(target = io.github.jasper.monitoring.api.MonitorActionAttributeTarget.RESOURCE_ID)
            String resourceId) {
            StreamingResponseBody body = output ->
                output.write(resourceId.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return (T) body;
        }

        @MonitorAction(action = "bounded-stream-array", enrichers = CountingFactsEnricher.class)
        @SuppressWarnings("unchecked")
        public <T extends StreamingResponseBody> T[] boundedStreamArray(
            @MonitorActionAttribute(target = io.github.jasper.monitoring.api.MonitorActionAttributeTarget.RESOURCE_ID)
            String resourceId) {
            StreamingResponseBody body = output ->
                output.write(resourceId.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return (T[]) new StreamingResponseBody[] {body};
        }

        @MonitorAction(action = "stream-array", enrichers = CountingFactsEnricher.class)
        public StreamingResponseBody[] streamArray(
            @MonitorActionAttribute(target = io.github.jasper.monitoring.api.MonitorActionAttributeTarget.RESOURCE_ID)
            String resourceId) {
            return new StreamingResponseBody[] {output ->
                output.write(resourceId.getBytes(java.nio.charset.StandardCharsets.UTF_8))};
        }

        @MonitorAction(action = "inherited-stream", enrichers = CountingFactsEnricher.class)
        public InheritedStreamingResponse inheritedStream(
            @MonitorActionAttribute(target = io.github.jasper.monitoring.api.MonitorActionAttributeTarget.RESOURCE_ID)
            String resourceId) {
            return new InheritedStreamingResponse(output ->
                output.write(resourceId.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        }

        @MonitorAction(action = "unresolved-type-variable", enrichers = CountingFactsEnricher.class)
        @SuppressWarnings("unchecked")
        public <T> T unresolvedTypeVariable(
            @MonitorActionAttribute(target = io.github.jasper.monitoring.api.MonitorActionAttributeTarget.RESOURCE_ID)
            String resourceId) {
            StreamingResponseBody body = output ->
                output.write(resourceId.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return (T) body;
        }

        @MonitorAction(action = "unbounded-wildcard", enrichers = CountingFactsEnricher.class)
        public ResponseEntity<?> unboundedWildcard(
            @MonitorActionAttribute(target = io.github.jasper.monitoring.api.MonitorActionAttributeTarget.RESOURCE_ID)
            String resourceId) {
            StreamingResponseBody body = output ->
                output.write(resourceId.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return ResponseEntity.ok((Object) body);
        }

        @MonitorAction(action = "unresolved-http-entity", enrichers = CountingFactsEnricher.class)
        @SuppressWarnings("unchecked")
        public <T> ResponseEntity<T> unresolvedHttpEntity(
            @MonitorActionAttribute(target = io.github.jasper.monitoring.api.MonitorActionAttributeTarget.RESOURCE_ID)
            String resourceId) {
            StreamingResponseBody body = output ->
                output.write(resourceId.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return (ResponseEntity<T>) (ResponseEntity<?>) ResponseEntity.ok(body);
        }

        @MonitorAction(action = "raw-http-entity", enrichers = CountingFactsEnricher.class)
        @SuppressWarnings("rawtypes")
        public ResponseEntity rawHttpEntity(
            @MonitorActionAttribute(target = io.github.jasper.monitoring.api.MonitorActionAttributeTarget.RESOURCE_ID)
            String resourceId) {
            StreamingResponseBody body = output ->
                output.write(resourceId.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return ResponseEntity.ok((Object) body);
        }

        @MonitorAction(action = "raw-safe-response", enrichers = CountingFactsEnricher.class)
        @SuppressWarnings("rawtypes")
        public SafeResponse rawSafeResponse(
            @MonitorActionAttribute(target = io.github.jasper.monitoring.api.MonitorActionAttributeTarget.RESOURCE_ID)
            String resourceId) {
            return new SafeResponse<String>("safe-response");
        }

        @MonitorAction(action = "safe-response", enrichers = CountingFactsEnricher.class)
        public SafeResponse<?> safeResponse(
            @MonitorActionAttribute(target = io.github.jasper.monitoring.api.MonitorActionAttributeTarget.RESOURCE_ID)
            String resourceId) {
            return new SafeResponse<String>("safe-response");
        }

        @MonitorAction(action = "map-response", enrichers = CountingFactsEnricher.class)
        public ResponseEntity<Map<String, Object>> mapResponse(
            @MonitorActionAttribute(target = io.github.jasper.monitoring.api.MonitorActionAttributeTarget.RESOURCE_ID)
            String resourceId) {
            Map<String, Object> body = new LinkedHashMap<String, Object>();
            body.put("id", resourceId);
            return ResponseEntity.ok(body);
        }

        @MonitorAction(action = "primary-same", enrichers = CountingFactsEnricher.class)
        public String same(String value) {
            return "primary";
        }
    }

    public static class UnrelatedController {
        @MonitorAction(action = "unrelated-same")
        public String same(
            @MonitorActionAttribute(target = io.github.jasper.monitoring.api.MonitorActionAttributeTarget.RESOURCE_ID)
            String resourceId) {
            return "unrelated";
        }
    }

    public static final class InheritedStreamingResponse extends ResponseEntity<StreamingResponseBody> {
        private InheritedStreamingResponse(StreamingResponseBody body) {
            super(body, org.springframework.http.HttpStatus.OK);
        }
    }

    public static final class SafeResponse<T extends String> extends ResponseEntity<T> {
        private SafeResponse(T body) {
            super(body, org.springframework.http.HttpStatus.OK);
        }
    }

    @MonitorAction(action = "interface-method-type", resourceType = "type",
        enrichers = CountingFactsEnricher.class)
    @MonitorActionAttribute(name = "type-source", value = "type")
    public interface InterfaceMethodController {
        @MonitorAction(eventType = SecurityEventType.EXPORT, action = "interface-method", resourceType = "report",
            enrichers = ReturningFactsEnricher.class)
        @MonitorActionAttribute(name = "method-source", value = "method")
        String export(
            @MonitorActionAttribute(target = io.github.jasper.monitoring.api.MonitorActionAttributeTarget.RESOURCE_ID)
            String resourceId,
            @MonitorActionAttribute(target = io.github.jasper.monitoring.api.MonitorActionAttributeTarget.ORG_SCOPE)
            String orgScope);
    }

    @org.springframework.stereotype.Controller
    public static class InterfaceMethodControllerImpl implements InterfaceMethodController {
        @Override
        public String export(String resourceId, String orgScope) {
            return "interface-method";
        }
    }

    public interface GenericInterfaceMethodController<T> {
        @MonitorAction(action = "generic-interface-method")
        String export(
            @MonitorActionAttribute(target = io.github.jasper.monitoring.api.MonitorActionAttributeTarget.RESOURCE_ID)
            T resourceId,
            @MonitorActionAttribute(target = io.github.jasper.monitoring.api.MonitorActionAttributeTarget.ORG_SCOPE)
            T orgScope);

        String export(CharSequence resourceId, CharSequence orgScope);
    }

    @org.springframework.stereotype.Controller
    public static class GenericInterfaceMethodControllerImpl
        implements GenericInterfaceMethodController<String> {
        @Override
        public String export(String resourceId, String orgScope) {
            return "generic-interface-method";
        }

        @Override
        public String export(CharSequence resourceId, CharSequence orgScope) {
            return "generic-interface-unrelated";
        }
    }

    @MonitorAction(action = "interface-type", resourceType = "tenant", enrichers = ReturningFactsEnricher.class)
    @MonitorActionAttribute(name = "type-source", value = "type")
    public interface InterfaceTypeController {
        String export(
            @MonitorActionAttribute(target = io.github.jasper.monitoring.api.MonitorActionAttributeTarget.RESOURCE_ID)
            String resourceId,
            @MonitorActionAttribute(target = io.github.jasper.monitoring.api.MonitorActionAttributeTarget.ORG_SCOPE)
            String orgScope);
    }

    @org.springframework.stereotype.Controller
    public static class InterfaceTypeControllerImpl implements InterfaceTypeController {
        @Override
        @MonitorActionAttribute(name = "implementation-source", value = "implementation")
        public String export(String resourceId, String orgScope) {
            return "interface-type";
        }
    }

    public static final class ReturningFactsEnricher implements MonitorActionEnricher {
        @Override
        public MonitorActionFacts enrich(MonitorActionInvocation invocation) {
            if (invocation.getPhase() != MonitorActionInvocation.Phase.AFTER_RETURNING) {
                return MonitorActionFacts.empty();
            }
            return MonitorActionFacts.builder().dataCount(7).result(SecurityEventResult.SUCCESS)
                .reasonCode("BUSINESS_OK").attribute("Classification", "dynamic").build();
        }
    }

    public static final class FailingFactsEnricher implements MonitorActionEnricher {
        @Override
        public MonitorActionFacts enrich(MonitorActionInvocation invocation) {
            throw new IllegalStateException("ignored");
        }
    }

    public static final class TagFactsEnricher implements MonitorActionEnricher {
        @Override
        public MonitorActionFacts enrich(MonitorActionInvocation invocation) {
            return MonitorActionFacts.builder().attribute("monitor.rule-tag.approved", "false")
                .attribute("monitor.rule-tag.injected", "false").build();
        }
    }

    public static final class CountingFactsEnricher implements MonitorActionEnricher {
        private static final AtomicInteger invocations = new AtomicInteger();

        static void reset() {
            invocations.set(0);
        }

        @Override
        public MonitorActionFacts enrich(MonitorActionInvocation invocation) {
            invocations.incrementAndGet();
            return MonitorActionFacts.empty();
        }
    }

    public static final class ExportRequest {
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

    public static final class Resource {
        private final String id;

        private Resource(String id) {
            this.id = id;
        }

        public String getId() {
            return id;
        }
    }

    public static final class Organization {
        private final String id;

        private Organization(String id) {
            this.id = id;
        }

        public String getId() {
            return id;
        }
    }

    public static final class Channel {
        private final String name;

        private Channel(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }

    public static final class ExplodingPayload {
        public String value = "must-not-leak";

        public String getValue() {
            throw new IllegalStateException("getter failure");
        }
    }

    private static final class CapturingSecurityMonitor implements SecurityMonitor {
        private final List<SecurityEventDraft> events = new ArrayList<SecurityEventDraft>();
        private final List<EventInputValidation> validations = new ArrayList<EventInputValidation>();

        @Override
        public MonitoringOutcome record(SecurityEventDraft draft) {
            events.add(draft);
            return null;
        }

        @Override
        public MonitoringOutcome record(SecurityEventDraft draft, EventInputValidation validation) {
            events.add(draft);
            validations.add(validation);
            return null;
        }
    }
}

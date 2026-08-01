package io.github.jasper.monitoring.spring3.autoconfigure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.jasper.monitoring.api.IdentityContext;
import io.github.jasper.monitoring.api.MonitoringContextAccessor;
import io.github.jasper.monitoring.api.MonitoringRequestContext;
import io.github.jasper.monitoring.api.SecurityEventResult;
import io.github.jasper.monitoring.api.action.ActionCatalog;
import io.github.jasper.monitoring.api.action.BuiltInActions;
import io.github.jasper.monitoring.api.action.MonitorAction;
import io.github.jasper.monitoring.api.code.BuiltInReasonCodes;
import io.github.jasper.monitoring.api.code.StableCodeCatalog;
import io.github.jasper.monitoring.api.fact.ActionFact;
import io.github.jasper.monitoring.api.fact.BuiltInFacts;
import io.github.jasper.monitoring.api.fact.FactCatalog;
import io.github.jasper.monitoring.api.fact.FactSource;
import io.github.jasper.monitoring.core.application.DefaultMonitoringRuntime;
import io.github.jasper.monitoring.core.application.MonitoringRuntimePort;
import io.github.jasper.monitoring.core.application.MonitoringService;
import io.github.jasper.monitoring.core.application.SecurityEventAssembler;
import io.github.jasper.monitoring.core.domain.SecurityEvent;
import io.github.jasper.monitoring.core.domain.EventFact;
import io.github.jasper.monitoring.core.port.EventRepository;
import io.github.jasper.monitoring.spring.support.ActionFactExtractor;
import io.github.jasper.monitoring.spring.support.MonitorActionContractValidator;
import io.github.jasper.monitoring.spring.support.MonitoringFacts;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.http.ResponseEntity;

class TypedMonitorActionAspectTest {
    @Test
    void combinesParameterAndRuntimeFactsWithIndependentSources() {
        Fixture fixture = new Fixture();
        MonitoredApi proxy = fixture.proxy();

        proxy.runtimeFact(Boolean.TRUE);

        assertEquals("true", fixture.fact("sensitive").getValueText());
        assertEquals(37L, fixture.events.last.getDataCount());
        assertEquals(FactSource.METHOD_PARAMETER,
            fixture.fact("sensitive").getSource());
        assertEquals(FactSource.HOST_PROVIDER,
            fixture.fact("data_count").getSource());
    }

    @Test
    void rejectsA_RUNTIMEFactThatDuplicatesAParameterFact() {
        Fixture fixture = new Fixture();
        MonitoredApi proxy = fixture.proxy();

        IllegalStateException failure = assertThrows(IllegalStateException.class,
            () -> proxy.duplicateFact(Long.valueOf(7L)));
        assertEquals(0, failure.getSuppressed().length);
    }

    @Test
    void includesRuntimeFactsInFailureEventsAndCleansTheScope() {
        Fixture fixture = new Fixture();
        MonitoredApi proxy = fixture.proxy();

        assertThrows(IllegalStateException.class, proxy::throwsAfterRuntimeFact);
        fixture.assertOutcome(SecurityEventResult.FAILURE, "MON.ACTION.INVOCATION_FAILED");
        assertEquals(41L, fixture.events.last.getDataCount());
        assertEquals(FactSource.HOST_PROVIDER, fixture.fact("data_count").getSource());
        assertEquals(false, MonitoringFacts.put(BuiltInFacts.DataCount.class, Long.valueOf(99L)));
    }

    @Test
    void isolatesFactsForNestedMonitoredProxyCalls() {
        Fixture fixture = new Fixture();
        MonitoredApi proxy = fixture.proxy();

        proxy.outer();

        assertEquals(2, fixture.events.saved.size());
        assertEquals(22L, fixture.events.saved.get(0).getDataCount());
        assertEquals(11L, fixture.events.saved.get(1).getDataCount());
    }

    @Test
    void resolvesReturnAndExceptionOutcomes() {
        Fixture fixture = new Fixture();
        MonitoredApi proxy = fixture.proxy();

        proxy.ok();
        fixture.assertOutcome(SecurityEventResult.SUCCESS, null);
        proxy.unauthorized();
        fixture.assertOutcome(SecurityEventResult.DENIED, "MON.ACTION.BLOCKED");
        proxy.forbidden();
        fixture.assertOutcome(SecurityEventResult.DENIED, "MON.ACTION.BLOCKED");
        proxy.badRequest();
        fixture.assertOutcome(SecurityEventResult.FAILURE, "MON.ACTION.REQUEST_FAILED");
        proxy.serverError();
        fixture.assertOutcome(SecurityEventResult.FAILURE, "MON.ACTION.REQUEST_FAILED");
        assertThrows(IllegalStateException.class, proxy::throwsFailure);
        fixture.assertOutcome(SecurityEventResult.FAILURE, "MON.ACTION.INVOCATION_FAILED");
    }

    interface MonitoredApi {
        String ok();
        ResponseEntity<Void> unauthorized();
        ResponseEntity<Void> forbidden();
        ResponseEntity<Void> badRequest();
        ResponseEntity<Void> serverError();
        String throwsFailure();
        String runtimeFact(Boolean sensitive);
        String duplicateFact(Long count);
        String throwsAfterRuntimeFact();
        String outer();
        String inner();
    }

    static class MonitoredService implements MonitoredApi {
        private MonitoredApi self;

        @Override @MonitorAction(BuiltInActions.Query.class) public String ok() { return "ok"; }
        @Override @MonitorAction(BuiltInActions.Query.class) public ResponseEntity<Void> unauthorized() { return ResponseEntity.status(401).build(); }
        @Override @MonitorAction(BuiltInActions.Query.class) public ResponseEntity<Void> forbidden() { return ResponseEntity.status(403).build(); }
        @Override @MonitorAction(BuiltInActions.Query.class) public ResponseEntity<Void> badRequest() { return ResponseEntity.badRequest().build(); }
        @Override @MonitorAction(BuiltInActions.Query.class) public ResponseEntity<Void> serverError() { return ResponseEntity.status(500).build(); }
        @Override @MonitorAction(BuiltInActions.Query.class) public String throwsFailure() { throw new IllegalStateException("failed"); }
        @Override @MonitorAction(BuiltInActions.SensitiveView.class)
        public String runtimeFact(@ActionFact(BuiltInFacts.Sensitive.class) Boolean sensitive) {
            MonitoringFacts.put(BuiltInFacts.DataCount.class, Long.valueOf(37L));
            return String.valueOf(sensitive);
        }
        @Override @MonitorAction(BuiltInActions.SensitiveView.class)
        public String duplicateFact(@ActionFact(BuiltInFacts.DataCount.class) Long count) {
            MonitoringFacts.put(BuiltInFacts.DataCount.class, Long.valueOf(8L));
            return String.valueOf(count);
        }
        @Override @MonitorAction(BuiltInActions.SensitiveView.class)
        public String throwsAfterRuntimeFact() {
            MonitoringFacts.put(BuiltInFacts.DataCount.class, Long.valueOf(41L));
            throw new IllegalStateException("failed-after-fact");
        }
        @Override @MonitorAction(BuiltInActions.SessionConcurrent.class)
        public String outer() {
            MonitoringFacts.put(BuiltInFacts.DataCount.class, Long.valueOf(11L));
            return self.inner();
        }
        @Override @MonitorAction(BuiltInActions.SessionConcurrent.class)
        public String inner() {
            MonitoringFacts.put(BuiltInFacts.DataCount.class, Long.valueOf(22L));
            return "inner";
        }
    }

    static final class Fixture {
        private final CapturingEvents events = new CapturingEvents();
        private final ActionCatalog actions = actions();
        private final FactCatalog facts = facts();

        MonitoredApi proxy() {
            MonitoringRuntimePort runtime = new DefaultMonitoringRuntime(actions, facts, Collections.emptyList());
            MonitoringService monitoring = new MonitoringService(events,
                new SecurityEventAssembler("test", Clock.fixed(Instant.parse("2026-07-26T00:00:00Z"), ZoneOffset.UTC)),
                runtime, (a, d, e, f, s, i, o) -> { }, stableCodes());
            MonitoringContextAccessor context = new MonitoringContextAccessor() {
                @Override public MonitoringRequestContext requestContext() {
                    return MonitoringRequestContext.builder().method("GET").path("/test")
                        .sourceIp("127.0.0.1").requestId("request-1").build();
                }
                @Override public IdentityContext identityContext() { return IdentityContext.anonymous(); }
            };
            TypedMonitorActionAspect aspect = new TypedMonitorActionAspect(monitoring, context,
                new ActionFactExtractor(facts), new MonitorActionContractValidator(actions, facts,
                    Collections.emptyList()));
            MonitoredService target = new MonitoredService();
            ProxyFactory factory = new ProxyFactory(target);
            factory.setProxyTargetClass(true);
            factory.addAdvisor(aspect);
            MonitoredApi proxy = (MonitoredApi) factory.getProxy();
            target.self = proxy;
            return proxy;
        }

        void assertOutcome(SecurityEventResult result, String reason) {
            assertEquals(result, events.last.getResult());
            assertEquals(reason, events.last.getReasonCode());
        }

        EventFact fact(String key) {
            for (EventFact fact : events.last.getFacts()) {
                if (key.equals(fact.getKey())) return fact;
            }
            throw new AssertionError("Missing event fact " + key);
        }
    }

    static final class CapturingEvents implements EventRepository {
        private SecurityEvent last;
        private final List<SecurityEvent> saved = new ArrayList<SecurityEvent>();
        @Override public void save(SecurityEvent event) { last = event; saved.add(event); }
        @Override public Optional<SecurityEvent> findEvent(String id) { return Optional.ofNullable(last); }
        @Override public List<SecurityEvent> findSince(String system, Instant since) { return Collections.emptyList(); }
    }

    private static ActionCatalog actions() { ActionCatalog c = new ActionCatalog(); BuiltInActions.registerInto(c); c.freeze(); return c; }
    private static StableCodeCatalog stableCodes() { StableCodeCatalog c = new StableCodeCatalog(""); BuiltInReasonCodes.registerInto(c); c.freeze(); return c; }
    private static FactCatalog facts() { FactCatalog c = new FactCatalog(); BuiltInFacts.registerInto(c); c.freeze(); return c; }
}

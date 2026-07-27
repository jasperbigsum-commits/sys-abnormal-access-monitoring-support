package io.github.jasper.monitoring.audit.spring2.report;

import io.github.jasper.monitoring.api.MonitoringContextAccessor;
import io.github.jasper.monitoring.api.action.BuiltInActions;
import io.github.jasper.monitoring.api.event.ActionExecution;
import io.github.jasper.monitoring.api.event.ActionOutcome;
import io.github.jasper.monitoring.api.fact.ActionFacts;
import io.github.jasper.monitoring.api.fact.BuiltInFacts;
import io.github.jasper.monitoring.api.fact.FactSource;
import io.github.jasper.monitoring.audit.spring2.persistence.AuditFixtureRepository;
import io.github.jasper.monitoring.core.application.MonitoringService;
import java.time.Clock;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/** Query business boundary whose decisions are observable through subsequent HTTP calls. */
@Service
public final class ReportQueryService {
    private final MonitoringService monitoring; private final MonitoringContextAccessor contexts;
    private final AuditFixtureRepository fixtures; private final Clock clock=Clock.systemUTC();
    public ReportQueryService(MonitoringService monitoring, MonitoringContextAccessor contexts,
                              AuditFixtureRepository fixtures) {
        this.monitoring=monitoring; this.contexts=contexts; this.fixtures=fixtures;
    }

    public HttpStatus query(String resourceId, boolean sequential, String sessionId) {
        String userId=contexts.identityContext().getUserId();
        if (sessionId!=null && !fixtures.isActiveSession(sessionId)) return HttpStatus.UNAUTHORIZED;
        if (fixtures.hasActiveControl(userId,"DENY",clock.instant())) return HttpStatus.FORBIDDEN;
        if (fixtures.hasActiveControl(userId,"RATE_LIMIT",clock.instant())) return HttpStatus.TOO_MANY_REQUESTS;
        ActionFacts facts=ActionFacts.builder().put(BuiltInFacts.ResourceId.class,resourceId)
            .put(BuiltInFacts.SequentialAccess.class,Boolean.toString(sequential)).build();
        monitoring.monitor(ActionExecution.of(BuiltInActions.Query.class,contexts.requestContext(),
            contexts.identityContext(),ActionOutcome.success(0L),facts,FactSource.HOST_PROVIDER));
        return HttpStatus.OK;
    }
}

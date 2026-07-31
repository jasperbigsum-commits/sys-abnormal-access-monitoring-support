package io.github.jasper.monitoring.audit.spring2.report;

import io.github.jasper.monitoring.api.MonitoringContextAccessor;
import io.github.jasper.monitoring.api.action.BuiltInActions;
import io.github.jasper.monitoring.api.event.ActionOutcome;
import io.github.jasper.monitoring.api.fact.ActionFacts;
import io.github.jasper.monitoring.api.fact.BuiltInFacts;
import io.github.jasper.monitoring.audit.spring2.persistence.AuditFixtureRepository;
import io.github.jasper.monitoring.spring.support.MonitoringRecorder;
import java.time.Clock;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * 报告查询业务边界和 Query Action 埋点适配器。
 *
 * <p>服务先检查会话和已生效控制，再以服务端资源 ID 和顺序访问事实调用监测服务。监测组件负责
 * 事件、规则、告警和控制编排；本次调用新命中的控制通常保护后续请求，不会回滚已经执行的查询。</p>
 */
@Service
public final class ReportQueryService {
    private final MonitoringRecorder monitoringRecorder; private final MonitoringContextAccessor contexts;
    private final AuditFixtureRepository fixtures; private final Clock clock=Clock.systemUTC();
    public ReportQueryService(MonitoringRecorder monitoringRecorder, MonitoringContextAccessor contexts,
                              AuditFixtureRepository fixtures) {
        this.monitoringRecorder=monitoringRecorder; this.contexts=contexts; this.fixtures=fixtures;
    }

    public HttpStatus query(String resourceId, boolean sequential, String sessionId) {
        String userId=contexts.identityContext().getUserId();
        if (sessionId!=null && !fixtures.isActiveSession(sessionId)) return HttpStatus.UNAUTHORIZED;
        if (fixtures.hasActiveControl(userId,"DENY",clock.instant())) return HttpStatus.FORBIDDEN;
        if (fixtures.hasActiveControl(userId,"RATE_LIMIT",clock.instant())) return HttpStatus.TOO_MANY_REQUESTS;
        ActionFacts facts=ActionFacts.builder().put(BuiltInFacts.ResourceId.class,resourceId)
            .put(BuiltInFacts.SequentialAccess.class, Boolean.valueOf(sequential)).build();
        monitoringRecorder.record(BuiltInActions.Query.class, ActionOutcome.success(0L), facts);
        return HttpStatus.OK;
    }
}

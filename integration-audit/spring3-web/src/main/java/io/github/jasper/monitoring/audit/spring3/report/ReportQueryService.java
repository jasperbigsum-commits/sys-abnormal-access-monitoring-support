package io.github.jasper.monitoring.audit.spring3.report;

import io.github.jasper.monitoring.api.MonitoringContextAccessor;
import io.github.jasper.monitoring.api.action.BuiltInActions;
import io.github.jasper.monitoring.api.event.ActionExecution;
import io.github.jasper.monitoring.api.event.ActionOutcome;
import io.github.jasper.monitoring.api.fact.ActionFacts;
import io.github.jasper.monitoring.api.fact.BuiltInFacts;
import io.github.jasper.monitoring.api.fact.FactSource;
import io.github.jasper.monitoring.audit.spring3.persistence.AuditFixtureRepository;
import io.github.jasper.monitoring.core.application.MonitoringService;
import java.time.Clock;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * 查询业务边界示例。
 *
 * <p>方法先检查会话和已生效控制，再提交 Query Action。sequential 和 resourceId 是宿主根据
 * 实际查询路径确定的 Fact，组件只负责校验、持久化和规则评估。TC-06、TC-07 通过后续 HTTP
 * 请求观察撤销会话和限流副作用。</p>
 **/
@Service
public final class ReportQueryService {
    private final MonitoringService monitoring; private final MonitoringContextAccessor contexts;
    private final AuditFixtureRepository fixtures; private final Clock clock=Clock.systemUTC();
    public ReportQueryService(MonitoringService monitoring, MonitoringContextAccessor contexts,
                              AuditFixtureRepository fixtures) {
        this.monitoring=monitoring; this.contexts=contexts; this.fixtures=fixtures;
    }

    /**
     * 执行一次受会话、控制和监测保护的查询。
     *
     * @param resourceId 服务端解析出的资源标识
     * @param sequential 是否由业务路径判定为顺序遍历
     * @param sessionId 验收用会话标识，可为空
     * @return 查询结果对应的 HTTP 状态
     */
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

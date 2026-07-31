package io.github.jasper.monitoring.audit.spring3.report;

import io.github.jasper.monitoring.api.MonitoringContextAccessor;
import io.github.jasper.monitoring.api.action.BuiltInActions;
import io.github.jasper.monitoring.api.event.ActionOutcome;
import io.github.jasper.monitoring.api.fact.ActionFacts;
import io.github.jasper.monitoring.api.fact.BuiltInFacts;
import io.github.jasper.monitoring.audit.spring3.persistence.AuditFixtureRepository;
import io.github.jasper.monitoring.spring.support.MonitoringRecorder;
import java.time.Clock;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * 查询业务边界示例。
 *
 * <p>方法先检查会话和已生效控制，再提交 Query Action。{@code sequential} 和 {@code resourceId}
 * 是宿主根据实际查询路径确定的 Fact，组件负责类型校验、持久化、规则评估、告警和控制编排。
 * 当前请求的前置控制检查由宿主完成；本次埋点新命中的控制一般在提交后生效，供后续请求检查。
 * TC-06、TC-07 通过后续 HTTP 请求观察撤销会话和限流副作用。</p>
 **/
@Service
public final class ReportQueryService {
    private final MonitoringRecorder monitoringRecorder;
    private final MonitoringContextAccessor contexts;
    private final AuditFixtureRepository fixtures;
    private final Clock clock = Clock.systemUTC();
    public ReportQueryService(MonitoringRecorder monitoringRecorder, MonitoringContextAccessor contexts,
                              AuditFixtureRepository fixtures) {
        this.monitoringRecorder = monitoringRecorder;
        this.contexts = contexts;
        this.fixtures = fixtures;
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
        String userId = contexts.identityContext().getUserId();
        if (sessionId != null && !fixtures.isActiveSession(sessionId)) {
            return HttpStatus.UNAUTHORIZED;
        }
        if (fixtures.hasActiveControl(userId, "DENY", clock.instant())) {
            return HttpStatus.FORBIDDEN;
        }
        if (fixtures.hasActiveControl(userId, "RATE_LIMIT", clock.instant())) {
            return HttpStatus.TOO_MANY_REQUESTS;
        }
        ActionFacts facts = ActionFacts.builder()
            .put(BuiltInFacts.ResourceId.class, resourceId)
            .put(BuiltInFacts.SequentialAccess.class, Boolean.valueOf(sequential)).build();
        monitoringRecorder.record(BuiltInActions.Query.class, ActionOutcome.success(0L), facts);
        return HttpStatus.OK;
    }
}

package io.github.jasper.monitoring.audit.spring3.control;

import io.github.jasper.monitoring.api.ControlActionType;
import io.github.jasper.monitoring.api.ControlTrigger;
import io.github.jasper.monitoring.core.domain.ControlCommand;
import io.github.jasper.monitoring.core.domain.ControlExecution;
import io.github.jasper.monitoring.audit.spring3.persistence.AuditFixtureRepository;
import java.time.Clock;
import org.springframework.stereotype.Component;

/**
 * 演示宿主仅通过注解声明可执行控制动作。
 *
 * <p>业务系统只需把真正的验证码、限流或锁定逻辑写在对应方法中；Starter 会自动将这些方法注册为
 * {@code ControlHandler}，无需另写适配器实现。</p>
 *
 * <p><strong>验收对照（注解声明层）</strong>：
 * IA-08（触发器声明合法且唯一）、IA-09（ENFORCE 必须有真实宿主处理器）、
 * IA-10（规则要求与控制覆盖一致）。</p>
 */
@Component("auditControlActions")
public final class AuditControlActions {
    private final AuditFixtureRepository fixtures;
    private final Clock clock = Clock.systemUTC();

    public AuditControlActions(AuditFixtureRepository fixtures) {
        this.fixtures = fixtures;
    }
    /**
     * 启用验证码挑战。
     *
     * <p><strong>用例编号</strong>：TC-01。</p>
     * <p><strong>验证核心点</strong>：连续失败后命中 REQUIRE_CAPTCHA，下一次登录必须先受挑战控制。</p>
     * <p><strong>注意细节</strong>：控制执行依赖幂等键，重复投递不能扩大副作用。</p>
     *
     * @param command 本次控制的主体、有效期和幂等键
     * @return 成功结果；真实宿主应在此调用验证码服务
     */
    @ControlTrigger(ControlActionType.REQUIRE_CAPTCHA)
    public ControlExecution requireCaptcha(ControlCommand command) {
        return activate(command);
    }

    /**
     * 对命中主体执行限流。
     *
     * <p><strong>用例编号</strong>：TC-01、TC-02、TC-07。</p>
     * <p><strong>验证核心点</strong>：能够按规则主体进行限流（账号或可信来源 IP），避免跨主体误伤。</p>
     * <p><strong>注意细节</strong>：主体必须来自服务端可信上下文，不得直接信任客户端伪造字段。</p>
     *
     * @param command 本次控制的主体、有效期和幂等键
     * @return 成功结果；真实宿主应在此调用限流服务并按幂等键去重
     */
    @ControlTrigger(ControlActionType.RATE_LIMIT)
    public ControlExecution rateLimit(ControlCommand command) {
        return activate(command);
    }

    /**
     * 撤销会话并保证幂等执行。
     *
     * <p><strong>用例编号</strong>：TC-06、TC-11、TC-13。</p>
     * <p><strong>验证核心点</strong>：同一幂等键并发/重放时只执行一次会话撤销副作用。</p>
     * <p><strong>注意细节</strong>：首次成功后保持同一控制状态，重复调用不得延长 TTL 或重复增量执行。</p>
     */
    @ControlTrigger(ControlActionType.REVOKE_SESSION)
    public ControlExecution revokeSession(ControlCommand command) {
        boolean first = fixtures.activateControl(command.getIdempotencyKey(), command.getSubject(),
            command.getAction().name(), command.getExpiresAt());
        if (first) {
            fixtures.revokeSessions(command.getSubject(), clock.instant());
        }
        return ControlExecution.succeeded(command.getIdempotencyKey());
    }

    /**
     * 启用 MFA 要求（当前夹具用于覆盖触发器声明能力）。
     *
     * <p><strong>验收编号</strong>：IA-08、IA-09、IA-10（声明合法性与覆盖完整性）。</p>
     * <p><strong>注意细节</strong>：若规则目录要求此动作，ENFORCE 模式必须存在可执行宿主处理器。</p>
     */
    @ControlTrigger(ControlActionType.REQUIRE_MFA)
    public ControlExecution requireMfa(ControlCommand command) {
        return activate(command);
    }

    /**
     * 拒绝动作控制。
     *
     * <p><strong>用例编号</strong>：TC-10。</p>
     * <p><strong>验证核心点</strong>：在权限提升等高风险场景中拒绝应先于业务状态提交。</p>
     * <p><strong>注意细节</strong>：拒绝是控制结果，不等于吞掉审计；事件与告警仍需完整记录。</p>
     */
    @ControlTrigger(ControlActionType.DENY)
    public ControlExecution deny(ControlCommand command) {
        return activate(command);
    }

    /**
     * 要求人工审批控制。
     *
     * <p><strong>用例编号</strong>：TC-08（高风险导出在生成文件前被阻断并要求审批）。</p>
     * <p><strong>验证核心点</strong>：审批要求必须在副作用发生前触发，保证“先控后执”。</p>
     * <p><strong>注意细节</strong>：审批控制与告警证据需可追溯关联，避免只拦截不留痕。</p>
     */
    @ControlTrigger(ControlActionType.REQUIRE_APPROVAL)
    public ControlExecution requireApproval(ControlCommand command) {
        return activate(command);
    }

    private ControlExecution activate(ControlCommand command) {
        fixtures.activateControl(command.getIdempotencyKey(), command.getSubject(), command.getAction().name(),
            command.getExpiresAt());
        return ControlExecution.succeeded(command.getIdempotencyKey());
    }
}

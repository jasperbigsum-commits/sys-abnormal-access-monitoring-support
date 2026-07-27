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
     * @param command 本次控制的主体、有效期和幂等键
     * @return 成功结果；真实宿主应在此调用限流服务并按幂等键去重
     */
    @ControlTrigger(ControlActionType.RATE_LIMIT)
    public ControlExecution rateLimit(ControlCommand command) {
        return activate(command);
    }

    @ControlTrigger(ControlActionType.REVOKE_SESSION)
    public ControlExecution revokeSession(ControlCommand command) {
        boolean first = fixtures.activateControl(command.getIdempotencyKey(), command.getSubject(),
            command.getAction().name(), command.getExpiresAt());
        if (first) {
            fixtures.revokeSessions(command.getSubject(), clock.instant());
        }
        return ControlExecution.succeeded(command.getIdempotencyKey());
    }

    @ControlTrigger(ControlActionType.REQUIRE_MFA)
    public ControlExecution requireMfa(ControlCommand command) {
        return activate(command);
    }

    @ControlTrigger(ControlActionType.DENY)
    public ControlExecution deny(ControlCommand command) {
        return activate(command);
    }

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

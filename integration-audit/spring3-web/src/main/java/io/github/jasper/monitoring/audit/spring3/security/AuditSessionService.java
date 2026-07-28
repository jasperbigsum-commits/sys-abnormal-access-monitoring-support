package io.github.jasper.monitoring.audit.spring3.security;

import io.github.jasper.monitoring.audit.spring3.persistence.AuditFixtureRepository;
import java.time.Clock;
import org.springframework.stereotype.Service;

/**
 * 宿主系统拥有的会话操作夹具。
 *
 * <p>接口边界可供生产参考；本类通过 {@code audit_session} 测试表模拟会话，必须替换为真实会话服务。</p>
 */
@Service
public final class AuditSessionService {
    // 集成夹具实现：使用 audit_session 测试表模拟宿主会话状态。
    private final AuditFixtureRepository fixtures; private final Clock clock = Clock.systemUTC();
    public AuditSessionService(AuditFixtureRepository fixtures) { this.fixtures=fixtures; }
    public int revokeAll(String userId) { return fixtures.revokeSessions(userId, clock.instant()); }
    public boolean active(String sessionId) { return fixtures.isActiveSession(sessionId); }
}

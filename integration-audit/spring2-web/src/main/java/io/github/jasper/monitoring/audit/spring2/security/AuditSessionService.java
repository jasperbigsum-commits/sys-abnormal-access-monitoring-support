package io.github.jasper.monitoring.audit.spring2.security;

import io.github.jasper.monitoring.audit.spring2.persistence.AuditFixtureRepository;
import java.time.Clock;
import org.springframework.stereotype.Service;

/** MyBatis-backed session operations owned by the host system. */
@Service
public final class AuditSessionService {
    private final AuditFixtureRepository fixtures; private final Clock clock = Clock.systemUTC();
    public AuditSessionService(AuditFixtureRepository fixtures) { this.fixtures=fixtures; }
    public int revokeAll(String userId) { return fixtures.revokeSessions(userId, clock.instant()); }
    public boolean active(String sessionId) { return fixtures.isActiveSession(sessionId); }
}

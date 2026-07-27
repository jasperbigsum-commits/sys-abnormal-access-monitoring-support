package io.github.jasper.monitoring.audit.spring2.persistence;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.stereotype.Repository;

/** Transaction-owning fixture repository; no production in-memory fallback exists. */
@Repository
public class AuditFixtureRepository {
    private final SqlSessionFactory sessions;

    public AuditFixtureRepository(SqlSessionFactory sessions) { this.sessions = sessions; }

    public Map<String, Object> findAccount(String userId) {
        try (SqlSession session = sessions.openSession()) {
            Map<String, Object> row = session.getMapper(AuditFixtureMapper.class).findAccount(userId);
            return row == null ? Collections.<String, Object>emptyMap() : row;
        }
    }

    public Map<String, Object> findReport(String reportId) {
        try (SqlSession session = sessions.openSession()) {
            Map<String, Object> row = session.getMapper(AuditFixtureMapper.class).findReport(reportId);
            return row == null ? Collections.<String, Object>emptyMap() : row;
        }
    }

    public List<String> findRoles(String userId) {
        try (SqlSession session = sessions.openSession()) {
            return session.getMapper(AuditFixtureMapper.class).findRoles(userId);
        }
    }

    public long countActiveSessions(String userId) {
        try (SqlSession session = sessions.openSession()) {
            return session.getMapper(AuditFixtureMapper.class).countActiveSessions(userId);
        }
    }

    public int createSession(String sessionId, String userId, Instant at) {
        return write(mapper -> mapper.insertSession(sessionId, userId, at));
    }

    public int revokeSessions(String userId, Instant at) {
        return write(mapper -> mapper.revokeSessions(userId, at));
    }

    public FixtureCounts counts() {
        try (SqlSession session = sessions.openSession()) {
            AuditFixtureMapper mapper = session.getMapper(AuditFixtureMapper.class);
            return new FixtureCounts(mapper.countControls(), mapper.countExports(), mapper.countNotificationAttempts());
        }
    }

    void seed() {
        try (SqlSession session = sessions.openSession(false)) {
            AuditFixtureMapper mapper = session.getMapper(AuditFixtureMapper.class);
            mapper.insertAccount("audit-viewer", "org-a", "ACTIVE");
            mapper.insertAccount("audit-exporter", "org-a", "ACTIVE");
            mapper.insertAccount("audit-admin", "org-a", "ACTIVE");
            mapper.insertAccount("audit-disabled", "org-a", "DISABLED");
            mapper.insertReport("report-a", "org-a", "NORMAL");
            mapper.insertReport("report-b", "org-b", "NORMAL");
            mapper.insertRole("audit-viewer", "audit-viewer", "fixture", Instant.EPOCH);
            mapper.insertRole("audit-exporter", "audit-exporter", "fixture", Instant.EPOCH);
            mapper.insertRole("audit-admin", "audit-admin", "fixture", Instant.EPOCH);
            session.commit();
        }
    }

    private int write(MapperWrite operation) {
        try (SqlSession session = sessions.openSession(false)) {
            int changed = operation.apply(session.getMapper(AuditFixtureMapper.class));
            session.commit();
            return changed;
        }
    }

    private interface MapperWrite { int apply(AuditFixtureMapper mapper); }

    public static final class FixtureCounts {
        private final long controls;
        private final long exports;
        private final long notificationAttempts;
        FixtureCounts(long controls, long exports, long notificationAttempts) {
            this.controls = controls; this.exports = exports; this.notificationAttempts = notificationAttempts;
        }
        public long getControls() { return controls; }
        public long getExports() { return exports; }
        public long getNotificationAttempts() { return notificationAttempts; }
    }
}

package io.github.jasper.monitoring.audit.spring2.privilege;

import io.github.jasper.monitoring.audit.spring2.persistence.AuditFixtureMapper;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.stereotype.Repository;

/** MyBatis transaction boundary for the reference host's role relation. */
@Repository
public class PrivilegeGrantRepository {
    private final SqlSessionFactory sessions;

    public PrivilegeGrantRepository(SqlSessionFactory sessions) {
        this.sessions = sessions;
    }

    /** Returns false after rolling back when an actor attempts to grant itself a role. */
    public boolean grantUnlessSelf(String actorId, String targetUserId, String roleId, Instant at) {
        try (SqlSession session = sessions.openSession(false)) {
            if (actorId.equals(targetUserId)) {
                session.rollback();
                return false;
            }
            session.getMapper(AuditFixtureMapper.class).insertRole(targetUserId, roleId, actorId, at);
            session.commit();
            return true;
        }
    }

    public List<String> findRoles(String userId) {
        try (SqlSession session = sessions.openSession()) {
            return session.getMapper(AuditFixtureMapper.class).findRoles(userId);
        }
    }
}

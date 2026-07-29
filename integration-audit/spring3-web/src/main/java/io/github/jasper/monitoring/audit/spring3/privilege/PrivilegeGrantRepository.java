package io.github.jasper.monitoring.audit.spring3.privilege;

import io.github.jasper.monitoring.audit.spring3.persistence.AuditFixtureMapper;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.stereotype.Repository;

/**
 * 参考宿主角色关系的 MyBatis 事务边界。
 *
 * <p>只操作验收夹具的 {@code audit_user_role}，用于验证自我提权在角色事务提交前被拒绝。
 * 生产系统应替换为自身角色/权限仓储，不能复用夹具表。</p>
 */
@Repository
public class PrivilegeGrantRepository {
    private final SqlSessionFactory sessions;

    public PrivilegeGrantRepository(SqlSessionFactory sessions) {
        this.sessions = sessions;
    }

    /**
     * 在角色关系事务中执行授予；操作者与目标用户相同时回滚并返回 {@code false}。
     *
     * @return 是否完成角色关系写入
     */
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

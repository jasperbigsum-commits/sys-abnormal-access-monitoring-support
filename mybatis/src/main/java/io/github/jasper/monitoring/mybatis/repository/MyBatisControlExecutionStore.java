package io.github.jasper.monitoring.mybatis.repository;

import io.github.jasper.monitoring.api.control.ControlStatus;
import io.github.jasper.monitoring.core.domain.ControlCommand;
import io.github.jasper.monitoring.core.domain.ControlExecution;
import io.github.jasper.monitoring.core.domain.control.StoredControl;
import io.github.jasper.monitoring.core.port.ControlExecutionStore;
import io.github.jasper.monitoring.core.port.AuthenticationControlRepository;
import io.github.jasper.monitoring.mybatis.MyBatisMonitoringStoreRegistrar;
import io.github.jasper.monitoring.mybatis.mapper.ControlMapper;
import io.github.jasper.monitoring.mybatis.po.ControlActionPo;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.ArrayList;
import java.util.List;
import org.apache.ibatis.exceptions.PersistenceException;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionManager;

/**
 * 基于 MyBatis 的控制状态存储。
 *
 * <p>实现使用唯一键预留和乐观锁状态迁移保证控制执行幂等，同时实现
 * {@link AuthenticationControlRepository}，为认证预检查询指定系统和主体上尚未过期的控制。</p>
 */
public final class MyBatisControlExecutionStore implements ControlExecutionStore, AuthenticationControlRepository {
    private final SqlSessionManager sessions;

    /**
     * 从 MyBatis 会话工厂创建控制状态存储并注册组件 Mapper。
     *
     * @param factory 宿主提供的 MyBatis 会话工厂
     */
    public MyBatisControlExecutionStore(SqlSessionFactory factory) {
        MyBatisMonitoringStoreRegistrar.register(Objects.requireNonNull(factory, "factory"));
        this.sessions = SqlSessionManager.newInstance(factory);
    }
    /**
     * 从已配置的会话管理器创建控制状态存储。
     *
     * @param sessions MyBatis 会话管理器
     */
    public MyBatisControlExecutionStore(SqlSessionManager sessions) { this.sessions = Objects.requireNonNull(sessions, "sessions"); }

    /** {@inheritDoc} */
    @Override public Optional<StoredControl> find(String key) {
        sessions.startManagedSession(true);
        try { return Optional.ofNullable(toStored(mapper().find(key))); }
        finally { sessions.close(); }
    }

    /** {@inheritDoc} */
    @Override public boolean reserve(ControlCommand command, ControlStatus initial, Instant at) {
        sessions.startManagedSession(false);
        try {
            ControlMapper mapper = mapper();
            mapper.reserve(command.getIdempotencyKey(), command.getIdempotencyKey(), command.getSystemId(),
                command.getAlertId(), command.getRuleId(), command.getSubject(), command.getAction().name(),
                command.getExpiresAt(), at);
            if (initial != ControlStatus.PENDING && mapper.transition(command.getIdempotencyKey(), 0,
                ControlStatus.PENDING.name(), initial.name(), null, at) != 1)
                throw new IllegalStateException("Could not establish initial control state: " + command.getIdempotencyKey());
            mapper.appendAttempt(command.getIdempotencyKey(), 1, initial.name(), null, at);
            sessions.commit(); return true;
        } catch (PersistenceException exception) {
            sessions.rollback();
            if (isUniqueViolation(exception)) return false;
            throw exception;
        } catch (RuntimeException exception) { sessions.rollback(); throw exception; }
        finally { sessions.close(); }
    }

    /** {@inheritDoc} */
    @Override public List<ControlCommand> findActive(String systemId, String subject, Instant at) {
        sessions.startManagedSession(true);
        try {
            List<ControlCommand> result = new ArrayList<ControlCommand>();
            for (ControlActionPo row : mapper().findActive(systemId, subject, at)) {
                result.add(toCommand(row));
            }
            return result;
        } finally { sessions.close(); }
    }

    /** {@inheritDoc} */
    @Override public StoredControl transition(String key, long version, ControlStatus expected,
                                               ControlStatus target, String reason, Instant at) {
        sessions.startManagedSession(false);
        try {
            ControlMapper mapper = mapper();
            if (mapper.transition(key, version, expected.name(), target.name(), reason, at) != 1)
                throw new IllegalStateException("Stale control state: " + key);
            ControlActionPo row = mapper.find(key);
            int attemptNo = mapper.maxAttempt(row.getControlId()) + 1;
            mapper.appendAttempt(row.getControlId(), attemptNo, target.name(), reason, at);
            sessions.commit(); return toStored(row);
        } catch (RuntimeException exception) { sessions.rollback(); throw exception; }
        finally { sessions.close(); }
    }

    private ControlMapper mapper() { return sessions.getMapper(ControlMapper.class); }
    private static StoredControl toStored(ControlActionPo row) {
        if (row == null) return null;
        return new StoredControl(ControlExecution.restored(row.getControlId(), row.getIdempotencyKey(),
            row.getStatus(), row.getFailureReason()), row.getVersion());
    }
    private static ControlCommand toCommand(ControlActionPo row) {
        return new ControlCommand(row.getSystemId(), row.getIdempotencyKey(), row.getAlertId(), row.getSubject(),
            row.getAction(), row.getExpiresAt(), row.getRuleId());
    }
    private static boolean isUniqueViolation(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException) {
                String state = ((SQLException) cause).getSQLState();
                return "23505".equals(state) || ("23000".equals(state) && ((SQLException) cause).getErrorCode() == 1062);
            }
        }
        return false;
    }
}

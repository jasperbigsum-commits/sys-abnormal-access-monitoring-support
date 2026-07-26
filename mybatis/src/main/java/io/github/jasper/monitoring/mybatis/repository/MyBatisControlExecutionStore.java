package io.github.jasper.monitoring.mybatis.repository;

import io.github.jasper.monitoring.api.control.ControlStatus;
import io.github.jasper.monitoring.core.domain.ControlCommand;
import io.github.jasper.monitoring.core.domain.ControlExecution;
import io.github.jasper.monitoring.core.domain.control.StoredControl;
import io.github.jasper.monitoring.core.port.ControlExecutionStore;
import io.github.jasper.monitoring.mybatis.MyBatisMonitoringRepositoryRegistrar;
import io.github.jasper.monitoring.mybatis.mapper.ControlMapper;
import io.github.jasper.monitoring.mybatis.po.ControlActionPo;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import org.apache.ibatis.exceptions.PersistenceException;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionManager;

/** MyBatis control state store with unique-key reservation and optimistic transitions. */
public final class MyBatisControlExecutionStore implements ControlExecutionStore {
    private final SqlSessionManager sessions;
    public MyBatisControlExecutionStore(SqlSessionFactory factory) {
        MyBatisMonitoringRepositoryRegistrar.register(Objects.requireNonNull(factory, "factory"));
        this.sessions = SqlSessionManager.newInstance(factory);
    }
    public MyBatisControlExecutionStore(SqlSessionManager sessions) { this.sessions = Objects.requireNonNull(sessions, "sessions"); }

    @Override public Optional<StoredControl> find(String key) {
        sessions.startManagedSession(true);
        try { return Optional.ofNullable(toStored(mapper().find(key))); }
        finally { sessions.close(); }
    }

    @Override public boolean reserve(ControlCommand command, ControlStatus initial, Instant at) {
        sessions.startManagedSession(false);
        try {
            ControlMapper mapper = mapper();
            mapper.reserve(command.getIdempotencyKey(), command.getIdempotencyKey(), command.getAlertId(),
                command.getRuleId(), command.getSubject(), command.getAction().name(), at);
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

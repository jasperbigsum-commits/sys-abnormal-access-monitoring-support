package io.github.jasper.monitoring.mybatis.repository;

import io.github.jasper.monitoring.api.ControlStatus;
import io.github.jasper.monitoring.core.domain.ControlCommand;
import io.github.jasper.monitoring.core.domain.ControlExecution;
import io.github.jasper.monitoring.core.domain.control.ControlAttempt;
import io.github.jasper.monitoring.core.port.ControlExecutionStore;
import io.github.jasper.monitoring.mybatis.mapper.ControlMapper;
import java.time.Instant;
import java.util.Objects;
import org.apache.ibatis.session.SqlSessionManager;

/** MyBatis reservation adapter; the insert uniqueness constraint is the concurrency gate. */
public final class MyBatisControlExecutionStore implements ControlExecutionStore {
    private final SqlSessionManager sessions;
    public MyBatisControlExecutionStore(SqlSessionManager sessions) { this.sessions = Objects.requireNonNull(sessions, "sessions"); }
    @Override public java.util.Optional<ControlExecution> find(String key) { return java.util.Optional.empty(); }
    @Override public boolean reserve(ControlCommand command) {
        sessions.startManagedSession(false);
        try {
            int inserted = mapper().reserve(command.getIdempotencyKey(), command.getIdempotencyKey(), command.getAlertId(), command.getRuleId(), command.getSubject(), command.getAction().name(), Instant.now());
            sessions.commit(); return inserted == 1;
        } catch (RuntimeException ex) { sessions.rollback(); return false; }
        finally { sessions.close(); }
    }
    @Override public void appendAttempt(ControlAttempt attempt) {
        sessions.startManagedSession(false);
        try { mapper().appendAttempt(attempt.getControlId(), attempt.getAttemptNo(), attempt.getStatus().name(), attempt.getFailureReason(), attempt.getAttemptedAt()); sessions.commit(); }
        finally { sessions.close(); }
    }
    @Override public void complete(String key, ControlExecution execution) {
        sessions.startManagedSession(false);
        try {
            if (mapper().completeReservation(key, execution.getStatus().name(), execution.getFailureReason(), Instant.now()) != 1)
                throw new IllegalStateException("Stale control reservation: " + key);
            sessions.commit();
        } catch (RuntimeException ex) { sessions.rollback(); throw ex; }
        finally { sessions.close(); }
    }
    private ControlMapper mapper() { return sessions.getMapper(ControlMapper.class); }
}

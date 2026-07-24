package io.github.jasper.monitoring.core.application;

import io.github.jasper.monitoring.core.domain.ControlCommand;
import io.github.jasper.monitoring.core.port.ControlHandler;
import io.github.jasper.monitoring.core.port.MonitoringRepository;
import io.github.jasper.monitoring.core.application.control.ControlHandlerRegistry;


import io.github.jasper.monitoring.core.domain.ControlExecution;
import io.github.jasper.monitoring.core.domain.ControlRecord;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

/**
 * 通过宿主处理器执行控制动作，并按幂等键持久化一次执行结果。
 *
 * <p>重复的幂等键会返回原结果，而不会再次调用宿主处理器。并发幂等性由
 * {@link MonitoringRepository} 的具体实现保证，生产环境应依赖数据库唯一约束或等效机制。</p>
 */
public final class DefaultControlService {
    private final MonitoringRepository repository;
    private final ControlHandlerRegistry handlers;
    private final Clock clock;
    /**
     * @param repository 用于幂等性和审计记录的持久化端口
     * @param handlers 具体控制动作的宿主实现
     * @param clock 审计时间来源
     */
    public DefaultControlService(MonitoringRepository repository, ControlHandlerRegistry handlers, Clock clock) {
        this.repository = repository;
        this.handlers = handlers;
        this.clock = clock;
    }
    /**
     * 首次执行一条指令，或返回标记为重放的已持久化结果。
     *
     * <p>处理器异常会被转换为失败结果，以免监测流程泄露宿主实现细节。</p>
     *
     * @param command 待执行的控制指令
     * @return 已持久化的执行结果
     */
    public ControlExecution execute(ControlCommand command) {
        Optional<ControlHandler> handler = handlers.find(command.getAction());
        Optional<ControlRecord> existing = repository.findControl(command.getIdempotencyKey());
        if (existing.isPresent() && !shouldRetryDefaultFallback(existing.get(), command, handler)) {
            return existing.get().getExecution().replay();
        }
        ControlExecution execution;
        if (!handler.isPresent()) {
            execution = ControlExecution.failed(command.getIdempotencyKey(), "No control handler for " + command.getAction());
        } else {
            try {
                execution = handler.get().execute(command);
                if (execution == null) {
                    execution = ControlExecution.failed(command.getIdempotencyKey(), "Control handler returned no result");
                }
            } catch (RuntimeException exception) {
                execution = ControlExecution.failed(command.getIdempotencyKey(), "Control handler failed");
            }
        }
        repository.saveControl(new ControlRecord(command, execution, Instant.now(clock)));
        return execution;
    }

    private static boolean shouldRetryDefaultFallback(ControlRecord existing, ControlCommand command,
                                                      Optional<ControlHandler> handler) {
        return existing.getCommand().getAction() == command.getAction()
            && handler.isPresent() && !handler.get().isFallback()
            && existing.getExecution().isDefaultFallback();
    }
}

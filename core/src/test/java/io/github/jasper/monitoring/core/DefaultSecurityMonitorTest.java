package io.github.jasper.monitoring.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.jasper.monitoring.api.ControlActionType;
import io.github.jasper.monitoring.api.MonitoringMode;
import io.github.jasper.monitoring.api.SecurityEventDraft;
import io.github.jasper.monitoring.api.SecurityEventResult;
import io.github.jasper.monitoring.api.SecurityEventType;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class DefaultSecurityMonitorTest {

    @Test
    void raisesOneDeduplicatedAlertOnlyAfterMoreThanFiveLoginFailuresInFiveMinutes() {
        InMemoryMonitoringRepository repository = new InMemoryMonitoringRepository();
        DefaultSecurityMonitor monitor = new DefaultSecurityMonitor(
            "orders",
            Clock.fixed(Instant.parse("2026-07-22T00:05:00Z"), ZoneOffset.UTC),
            repository,
            DefaultRuleCatalog.initialRules(),
            MonitoringMode.OBSERVE,
            ControlHandlerRegistry.empty(),
            NotificationChannel.noop());

        for (int i = 0; i < 5; i++) {
            monitor.record(loginFailure("alice", "req-" + i,
                Instant.parse("2026-07-22T00:0" + i + ":00Z")));
        }

        assertEquals(0, repository.getAlerts().size());

        MonitoringOutcome outcome = monitor.record(loginFailure("alice", "req-6", Instant.parse("2026-07-22T00:04:30Z")));

        assertEquals(1, repository.getAlerts().size());
        assertEquals("AUTH-01", repository.getAlerts().get(0).getRuleId());
        assertEquals(1, repository.getAlerts().get(0).getEventCount());
        assertTrue(outcome.hasRisk(ControlActionType.REQUIRE_CAPTCHA));
        assertTrue(outcome.hasRisk(ControlActionType.RATE_LIMIT));

        monitor.record(loginFailure("alice", "req-7", Instant.parse("2026-07-22T00:04:40Z")));

        assertEquals(1, repository.getAlerts().size());
        assertEquals(2, repository.getAlerts().get(0).getEventCount());
    }

    @Test
    void enforcesLargeExportOnceForTheSameIdempotencyKey() {
        AtomicInteger executions = new AtomicInteger();
        ControlHandler handler = new ControlHandler() {
            @Override
            public boolean supports(ControlActionType action) {
                return action == ControlActionType.DENY;
            }

            @Override
            public ControlExecution execute(ControlCommand command) {
                executions.incrementAndGet();
                return ControlExecution.succeeded(command.getIdempotencyKey());
            }
        };
        InMemoryMonitoringRepository repository = new InMemoryMonitoringRepository();
        DefaultSecurityMonitor monitor = new DefaultSecurityMonitor(
            "orders",
            Clock.fixed(Instant.parse("2026-07-22T00:00:00Z"), ZoneOffset.UTC),
            repository,
            DefaultRuleCatalog.initialRules(),
            MonitoringMode.ENFORCE,
            new ControlHandlerRegistry(Arrays.asList(handler)),
            NotificationChannel.noop());

        SecurityEventDraft export = SecurityEventDraft.builder()
            .eventType(SecurityEventType.EXPORT)
            .action("EXPORT")
            .result(SecurityEventResult.SUCCESS)
            .sourceIp("203.0.113.9")
            .requestId("export-1")
            .userId("alice")
            .dataCount(5000)
            .occurredAt(Instant.parse("2026-07-22T00:00:00Z"))
            .build();

        assertTrue(monitor.record(export).hasRisk(ControlActionType.DENY));
        assertEquals(1, executions.get());

        ControlCommand repeat = repository.getControls().get(0).getCommand();
        ControlExecution repeated = monitor.getControlService().execute(repeat);

        assertTrue(repeated.isIdempotentReplay());
        assertEquals(1, executions.get());
    }

    @Test
    void enforcesAThirtyMinuteIpRateLimitForMultiAccountFailures() {
        AtomicReference<ControlCommand> executed = new AtomicReference<ControlCommand>();
        ControlHandler handler = new ControlHandler() {
            @Override
            public boolean supports(ControlActionType action) {
                return action == ControlActionType.RATE_LIMIT;
            }

            @Override
            public ControlExecution execute(ControlCommand command) {
                executed.set(command);
                return ControlExecution.succeeded(command.getIdempotencyKey());
            }
        };
        Instant now = Instant.parse("2026-07-22T00:00:00Z");
        DefaultSecurityMonitor monitor = new DefaultSecurityMonitor(
            "orders", Clock.fixed(now, ZoneOffset.UTC), new InMemoryMonitoringRepository(),
            DefaultRuleCatalog.initialRules(), MonitoringMode.ENFORCE,
            new ControlHandlerRegistry(Arrays.asList(handler)), NotificationChannel.noop());

        for (int i = 0; i < 10; i++) {
            monitor.record(loginFailure("account-" + i, "ip-failure-" + i, now));
        }

        assertEquals("ip:203.0.113.8", executed.get().getSubject());
        assertEquals(now.plus(Duration.ofMinutes(30)), executed.get().getExpiresAt());
    }

    @Test
    void ignoresExpiredWhitelistAndStillRaisesAlert() {
        InMemoryMonitoringRepository repository = new InMemoryMonitoringRepository();
        repository.addWhitelist(new WhitelistEntry(
            "AUTH-03", "disabled-user", Instant.parse("2026-07-21T23:59:59Z")));
        DefaultSecurityMonitor monitor = new DefaultSecurityMonitor(
            "orders",
            Clock.fixed(Instant.parse("2026-07-22T00:00:00Z"), ZoneOffset.UTC),
            repository,
            DefaultRuleCatalog.initialRules(),
            MonitoringMode.OBSERVE,
            ControlHandlerRegistry.empty(),
            NotificationChannel.noop());

        SecurityEventDraft disabledLogin = SecurityEventDraft.builder()
            .eventType(SecurityEventType.LOGIN_FAILURE)
            .action("LOGIN")
            .result(SecurityEventResult.DENIED)
            .sourceIp("203.0.113.9")
            .requestId("disabled-login")
            .userId("disabled-user")
            .attribute("account_status", "DISABLED")
            .occurredAt(Instant.parse("2026-07-22T00:00:00Z"))
            .build();

        assertFalse(monitor.record(disabledLogin).getAlerts().isEmpty());
        assertEquals("AUTH-03", repository.getAlerts().get(0).getRuleId());
    }

    private SecurityEventDraft loginFailure(String userId, String requestId, Instant occurredAt) {
        return SecurityEventDraft.builder()
            .eventType(SecurityEventType.LOGIN_FAILURE)
            .action("LOGIN")
            .result(SecurityEventResult.FAILURE)
            .sourceIp("203.0.113.8")
            .requestId(requestId)
            .userId(userId)
            .occurredAt(occurredAt)
            .build();
    }
}

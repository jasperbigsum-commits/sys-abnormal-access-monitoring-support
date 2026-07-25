package io.github.jasper.monitoring.core;

import io.github.jasper.monitoring.core.domain.WhitelistEntry;
import io.github.jasper.monitoring.core.domain.ControlCommand;
import io.github.jasper.monitoring.core.port.ControlHandler;
import io.github.jasper.monitoring.core.application.control.ControlHandlerRegistry;
import io.github.jasper.monitoring.core.infrastructure.memory.InMemoryMonitoringRepository;
import io.github.jasper.monitoring.core.domain.rule.DefaultRuleCatalog;
import io.github.jasper.monitoring.core.domain.ControlExecution;
import io.github.jasper.monitoring.core.port.NotificationChannel;
import io.github.jasper.monitoring.core.application.DefaultSecurityMonitor;
import io.github.jasper.monitoring.core.application.MonitoringOutcome;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import io.github.jasper.monitoring.api.ControlActionType;
import io.github.jasper.monitoring.api.EventFactSource;
import io.github.jasper.monitoring.api.EventInputIssue;
import io.github.jasper.monitoring.api.EventInputIssueCode;
import io.github.jasper.monitoring.api.EventInputValidation;
import io.github.jasper.monitoring.api.RiskLevel;
import io.github.jasper.monitoring.api.MonitoringMode;
import io.github.jasper.monitoring.api.SecurityEventDraft;
import io.github.jasper.monitoring.api.SecurityEventResult;
import io.github.jasper.monitoring.api.SecurityEventType;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class DefaultSecurityMonitorTest {

    @Test
    void appendixBTc01RaisesOneDeduplicatedAlertAfterFiveLoginFailuresInFiveMinutes() {
        InMemoryMonitoringRepository repository = new InMemoryMonitoringRepository();
        DefaultSecurityMonitor monitor = new DefaultSecurityMonitor(
            "orders",
            Clock.fixed(Instant.parse("2026-07-22T00:05:00Z"), ZoneOffset.UTC),
            repository,
            DefaultRuleCatalog.initialRules(),
            MonitoringMode.OBSERVE,
            ControlHandlerRegistry.empty(),
            NotificationChannel.noop());

        for (int i = 0; i < 4; i++) {
            monitor.record(loginFailure("alice", "req-" + i,
                Instant.parse("2026-07-22T00:0" + i + ":00Z")));
        }

        assertEquals(0, repository.getAlerts().size());

        MonitoringOutcome outcome = monitor.record(loginFailure("alice", "req-5", Instant.parse("2026-07-22T00:04:00Z")));

        assertEquals(1, repository.getAlerts().size());
        assertEquals("AUTH-01", repository.getAlerts().get(0).getRuleId());
        assertEquals(1, repository.getAlerts().get(0).getEventCount());
        assertTrue(outcome.hasRisk(ControlActionType.REQUIRE_CAPTCHA));
        assertTrue(outcome.hasRisk(ControlActionType.RATE_LIMIT));

        monitor.record(loginFailure("alice", "req-6", Instant.parse("2026-07-22T00:04:30Z")));

        assertEquals(1, repository.getAlerts().size());
        assertEquals(2, repository.getAlerts().get(0).getEventCount());
    }

    @Test
    void appendixBTc08EnforcesLargeExportOnceForTheSameIdempotencyKey() {
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

        SecurityEventDraft export = export("export-1", 5000, Instant.parse("2026-07-22T00:00:00Z"));

        assertTrue(monitor.record(export).hasRisk(ControlActionType.DENY));
        assertEquals(1, executions.get());

        ControlCommand repeat = repository.getControls().get(0).getCommand();
        ControlExecution repeated = monitor.getControlService().execute(repeat);

        assertTrue(repeated.isIdempotentReplay());
        assertEquals(1, executions.get());
    }

    @Test
    void appendixBTc02EnforcesAThirtyMinuteIpRateLimitForMultiAccountFailures() {
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
        assertEquals("AUTH-02", executed.get().getRuleId());
        assertEquals(now.plus(Duration.ofMinutes(30)), executed.get().getExpiresAt());
    }

    @Test
    void appendixBTc09MatchesDailyCumulativeExportsWithoutAIndividuallyLargeExport() {
        InMemoryMonitoringRepository repository = new InMemoryMonitoringRepository();
        DefaultSecurityMonitor monitor = new DefaultSecurityMonitor(
            "orders", Clock.fixed(Instant.parse("2026-07-22T12:00:00Z"), ZoneOffset.UTC), repository,
            DefaultRuleCatalog.initialRules(), MonitoringMode.OBSERVE,
            ControlHandlerRegistry.empty(), NotificationChannel.noop());

        monitor.record(export("export-1", 4000, Instant.parse("2026-07-22T08:00:00Z")));
        monitor.record(export("export-2", 3000, Instant.parse("2026-07-22T09:00:00Z")));
        MonitoringOutcome outcome = monitor.record(export("export-3", 3000, Instant.parse("2026-07-22T10:00:00Z")));

        assertEquals(1, outcome.getMatches().size());
        assertEquals("EXPT-02", outcome.getMatches().get(0).getRuleId());
    }

    @Test
    void dailyCumulativeExportDoesNotCountLaterOutOfOrderEvents() {
        InMemoryMonitoringRepository repository = new InMemoryMonitoringRepository();
        DefaultSecurityMonitor monitor = new DefaultSecurityMonitor(
            "orders", Clock.fixed(Instant.parse("2026-07-22T12:00:00Z"), ZoneOffset.UTC), repository,
            DefaultRuleCatalog.initialRules(), MonitoringMode.OBSERVE,
            ControlHandlerRegistry.empty(), NotificationChannel.noop());

        monitor.record(export("export-before", 3000, Instant.parse("2026-07-22T08:00:00Z")));
        monitor.record(export("export-after", 4000, Instant.parse("2026-07-22T11:00:00Z")));
        MonitoringOutcome outcome = monitor.record(
            export("export-current", 3000, Instant.parse("2026-07-22T10:00:00Z")));

        assertTrue(outcome.getMatches().isEmpty());
    }

    @Test
    void appendixBTc07RaisesAnAlertOnTheOneHundredAndTwentiethQueryInFiveMinutes() {
        InMemoryMonitoringRepository repository = new InMemoryMonitoringRepository();
        DefaultSecurityMonitor monitor = new DefaultSecurityMonitor(
            "orders", Clock.fixed(Instant.parse("2026-07-22T00:00:00Z"), ZoneOffset.UTC), repository,
            DefaultRuleCatalog.initialRules(), MonitoringMode.OBSERVE,
            ControlHandlerRegistry.empty(), NotificationChannel.noop());

        for (int index = 0; index < 119; index++) {
            monitor.record(query("query-" + index, Instant.parse("2026-07-22T00:00:00Z")));
        }

        assertEquals(0, repository.getAlerts().size());

        MonitoringOutcome outcome = monitor.record(query("query-120", Instant.parse("2026-07-22T00:00:00Z")));

        assertEquals("DATA-01", outcome.getMatches().get(0).getRuleId());
        assertTrue(outcome.hasRisk(ControlActionType.RATE_LIMIT));
    }

    @Test
    void appendixBTc12IgnoresExpiredWhitelistAndStillRaisesAlert() {
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

    @Test
    void persistsAnIncompleteExportWithoutCreatingExportMatchesOrControls() {
        InMemoryMonitoringRepository repository = new InMemoryMonitoringRepository();
        DefaultSecurityMonitor monitor = new DefaultSecurityMonitor(
            "orders", Clock.fixed(Instant.parse("2026-07-22T00:00:00Z"), ZoneOffset.UTC), repository,
            DefaultRuleCatalog.initialRules(), MonitoringMode.OBSERVE,
            ControlHandlerRegistry.empty(), NotificationChannel.noop());
        SecurityEventDraft missingDataCount = SecurityEventDraft.builder()
            .eventType(SecurityEventType.EXPORT)
            .action("EXPORT")
            .result(SecurityEventResult.SUCCESS)
            .sourceIp("203.0.113.9")
            .requestId("export-without-count")
            .userId("alice")
            .attribute("sensitivity", "HIGH")
            .occurredAt(Instant.parse("2026-07-22T00:00:00Z"))
            .build();

        MonitoringOutcome outcome = monitor.record(missingDataCount);

        assertEquals("INCOMPLETE", outcome.getEvent().getInputStatus().name());
        assertTrue(outcome.getMatches().isEmpty());
        assertTrue(outcome.getControls().isEmpty());
        assertEquals(1, repository.getEvents().size());
    }

    @Test
    void isolatesInputIssueReporterFailureAfterTheEventTransaction() {
        AtomicInteger reports = new AtomicInteger();
        InMemoryMonitoringRepository repository = new InMemoryMonitoringRepository();
        DefaultSecurityMonitor monitor = new DefaultSecurityMonitor(
            "orders", Clock.fixed(Instant.parse("2026-07-22T00:00:00Z"), ZoneOffset.UTC), repository,
            DefaultRuleCatalog.initialRules(), MonitoringMode.OBSERVE,
            ControlHandlerRegistry.empty(), NotificationChannel.noop(),
            (draft, enabledRuleIds) -> EventInputValidation.incomplete(
                Arrays.asList(EventInputIssue.missing("EXPT-01", "dataCount", EventFactSource.SERVER_COMPUTED)),
                Arrays.asList("EXPT-01")),
            (draft, validation) -> {
                assertEquals(1, repository.getEvents().size());
                reports.incrementAndGet();
                throw new IllegalStateException("reporter unavailable");
            });

        MonitoringOutcome outcome = monitor.record(export("reporter-failure", 10,
            Instant.parse("2026-07-22T00:00:00Z")));

        assertEquals("INCOMPLETE", outcome.getEvent().getInputStatus().name());
        assertEquals(1, reports.get());
        assertEquals(1, repository.getEvents().size());
    }

    @Test
    void honorsExternalIneligibleRuleIdsEvenWhenTheyMatchAConfiguredRule() {
        InMemoryMonitoringRepository repository = new InMemoryMonitoringRepository();
        DefaultSecurityMonitor monitor = new DefaultSecurityMonitor(
            "orders", Clock.fixed(Instant.parse("2026-07-22T00:00:00Z"), ZoneOffset.UTC), repository,
            Collections.<io.github.jasper.monitoring.core.domain.rule.DetectionRule>singletonList(
                new io.github.jasper.monitoring.core.domain.rule.DetectionRule() {
                    @Override
                    public String getRuleId() {
                        return "MONITOR-ACTION";
                    }

                    @Override
                    public Optional<io.github.jasper.monitoring.core.domain.RuleMatch> evaluate(
                        io.github.jasper.monitoring.core.domain.SecurityEvent event,
                        java.util.List<io.github.jasper.monitoring.core.domain.SecurityEvent> history) {
                        return Optional.of(new io.github.jasper.monitoring.core.domain.RuleMatch("MONITOR-ACTION",
                            RiskLevel.LOW, event.subject(), "", "host rule", Collections.singletonList(
                                ControlActionType.RECORD)));
                    }
                }),
            MonitoringMode.OBSERVE, ControlHandlerRegistry.empty(), NotificationChannel.noop());
        EventInputIssue annotationIssue = EventInputIssue.of("MONITOR-ACTION", "resourceId",
            EventInputIssueCode.UNRESOLVED_PARAMETER_PATH, EventFactSource.METHOD_PARAMETER);

        MonitoringOutcome outcome = monitor.record(query("annotation-diagnostic",
            Instant.parse("2026-07-22T00:00:00Z")), EventInputValidation.incomplete(
            Collections.singletonList(annotationIssue), Collections.singleton("MONITOR-ACTION")));

        assertEquals("INCOMPLETE", outcome.getEvent().getInputStatus().name());
        assertTrue(outcome.getMatches().isEmpty());
        assertEquals(Collections.singletonList(annotationIssue), repository.getEvents().get(0).getInputIssues());
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

    private SecurityEventDraft export(String requestId, long dataCount, Instant occurredAt) {
        return SecurityEventDraft.builder()
            .eventType(SecurityEventType.EXPORT)
            .action("EXPORT")
            .result(SecurityEventResult.SUCCESS)
            .sourceIp("203.0.113.9")
            .requestId(requestId)
            .userId("alice")
            .dataCount(dataCount)
            .occurredAt(occurredAt)
            .build();
    }

    private SecurityEventDraft query(String requestId, Instant occurredAt) {
        return SecurityEventDraft.builder()
            .eventType(SecurityEventType.QUERY)
            .action("QUERY")
            .result(SecurityEventResult.SUCCESS)
            .sourceIp("203.0.113.9")
            .requestId(requestId)
            .userId("alice")
            .occurredAt(occurredAt)
            .build();
    }
}

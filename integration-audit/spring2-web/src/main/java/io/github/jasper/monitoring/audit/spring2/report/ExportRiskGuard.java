package io.github.jasper.monitoring.audit.spring2.report;

import io.github.jasper.monitoring.audit.spring2.persistence.AuditFixtureRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.stereotype.Component;

/** Server-side preflight for single, sensitive, and UTC-daily export risk. */
@Component
public final class ExportRiskGuard {
    private static final long SINGLE_EXPORT_LIMIT = 5000L;
    private static final long DAILY_EXPORT_LIMIT = 10000L;

    private final AuditFixtureRepository fixtures;
    private final Clock clock = Clock.systemUTC();

    public ExportRiskGuard(AuditFixtureRepository fixtures) {
        this.fixtures = fixtures;
    }

    public Decision evaluate(String userId, long rows, List<String> requestedFields) {
        Instant start = clock.instant().atZone(ZoneOffset.UTC).truncatedTo(ChronoUnit.DAYS).toInstant();
        long exported = fixtures.sumExports(userId, start, start.plus(1, ChronoUnit.DAYS));
        boolean sensitive = requestedFields != null && requestedFields.contains("sensitiveValue");
        return new Decision(rows >= SINGLE_EXPORT_LIMIT || sensitive || exported + rows >= DAILY_EXPORT_LIMIT,
            sensitive, exported);
    }
    public static final class Decision{
        private final boolean blocked;
        private final boolean sensitive;
        private final long dailyRows;

        Decision(boolean blocked, boolean sensitive, long dailyRows) {
            this.blocked = blocked;
            this.sensitive = sensitive;
            this.dailyRows = dailyRows;
        }

        public boolean isBlocked() {
            return blocked;
        }

        public boolean isSensitive() {
            return sensitive;
        }

        public long getDailyRows() {
            return dailyRows;
        }
    }
}

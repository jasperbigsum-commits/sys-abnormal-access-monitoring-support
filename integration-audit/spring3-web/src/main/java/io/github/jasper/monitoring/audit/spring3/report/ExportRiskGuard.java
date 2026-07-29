package io.github.jasper.monitoring.audit.spring3.report;

import io.github.jasper.monitoring.audit.spring3.persistence.AuditFixtureRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 导出前的服务端风险预检。
 *
 * <p>本夹具按 UTC 日累计、单次行数和请求字段是否包含敏感列进行判断。它只返回策略决定，
 * 不生成告警、不执行控制，也不生成文件；调用方负责先记录结果，再决定是否继续导出。</p>
 */
@Component
public final class ExportRiskGuard {
    private static final long SINGLE_EXPORT_LIMIT = 5000L;
    private static final long DAILY_EXPORT_LIMIT = 10000L;

    private final AuditFixtureRepository fixtures;
    private final Clock clock = Clock.systemUTC();

    public ExportRiskGuard(AuditFixtureRepository fixtures) {
        this.fixtures = fixtures;
    }

    /**
     * 重新从宿主台账读取日累计，并计算本次导出是否需要阻断。
     *
     * <p>requestedFields 只影响“是否请求敏感列”的策略判断。rows 必须由调用方从服务端数据集
     * 统计后传入；本方法不接受客户端自报数量作为权威来源。</p>
     *
     * @param userId 当前可信身份
     * @param rows 服务端统计的本次数据量
     * @param requestedFields 客户端选择的字段集合
     * @return 风险决定和预检前的 UTC 日累计
     */
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

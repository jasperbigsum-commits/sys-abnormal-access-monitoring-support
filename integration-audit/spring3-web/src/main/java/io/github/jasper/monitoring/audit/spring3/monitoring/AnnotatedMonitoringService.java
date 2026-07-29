package io.github.jasper.monitoring.audit.spring3.monitoring;

import io.github.jasper.monitoring.api.action.BuiltInActions;
import io.github.jasper.monitoring.api.action.MonitorAction;
import io.github.jasper.monitoring.api.fact.BuiltInFacts;
import io.github.jasper.monitoring.spring.support.MonitoringFacts;
import org.springframework.stereotype.Service;

/** Ordinary business service used to audit annotation-scoped runtime facts. */
@Service
public class AnnotatedMonitoringService {
    private static final long SERVER_REPORTED_ROW_COUNT = 37L;

    @MonitorAction(BuiltInActions.SensitiveView.class)
    public long export(AuditExportRequest ignored) {
        MonitoringFacts.put(BuiltInFacts.DataCount.class,
            Long.valueOf(SERVER_REPORTED_ROW_COUNT));
        return SERVER_REPORTED_ROW_COUNT;
    }
}

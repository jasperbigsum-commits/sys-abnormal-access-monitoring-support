package io.github.jasper.monitoring.audit.spring3.report;

import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Service;

/** Observable report side effect used to prove authorization happens before business work. */
@Service
public final class AuditExportService {
    private final AtomicInteger invocations = new AtomicInteger();

    public void export(AuditReportCatalog.AuditReport report) {
        if (report == null) {
            throw new IllegalArgumentException("report is required");
        }
        invocations.incrementAndGet();
    }

    public int getInvocationCount() { return invocations.get(); }
    public void reset() { invocations.set(0); }
}

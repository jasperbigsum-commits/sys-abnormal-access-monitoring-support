package io.github.jasper.monitoring.audit.spring3;

import java.util.concurrent.atomic.AtomicInteger;

/** Observable business side effect for the authorization acceptance fixture. */
public final class AuditExportService {
    private final AtomicInteger invocationCount = new AtomicInteger();

    void export(AuditReportCatalog.AuditReport report) {
        invocationCount.incrementAndGet();
    }

    public int getInvocationCount() {
        return invocationCount.get();
    }

    public void reset() {
        invocationCount.set(0);
    }
}

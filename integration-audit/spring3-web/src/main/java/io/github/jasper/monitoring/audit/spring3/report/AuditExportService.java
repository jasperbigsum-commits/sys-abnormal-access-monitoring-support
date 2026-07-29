package io.github.jasper.monitoring.audit.spring3.report;

import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Service;

/**
 * 可观察的报告导出业务副作用夹具。
 *
 * <p>每次真正进入该服务都会增加调用计数，测试据此证明授权拒绝发生在业务副作用之前。
 * 本类不生成文件、不执行真实数据查询，也不负责监测；生产系统应由真实导出 Service 承担这些职责，
 * 并在授权和风险预检通过后才执行。</p>
 */
@Service
public final class AuditExportService {
    private final AtomicInteger invocations = new AtomicInteger();

    /** 记录一次已经通过授权并进入导出业务的调用。 */
    public void export(AuditReportCatalog.AuditReport report) {
        if (report == null) {
            throw new IllegalArgumentException("report is required");
        }
        invocations.incrementAndGet();
    }

    /** @return 导出业务实际进入次数，用于断言授权拒绝没有发生业务副作用 */
    public int getInvocationCount() { return invocations.get(); }

    /** 清除本次验收前的导出调用计数。 */
    public void reset() { invocations.set(0); }
}

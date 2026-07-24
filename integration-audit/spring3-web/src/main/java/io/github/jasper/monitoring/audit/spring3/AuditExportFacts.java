package io.github.jasper.monitoring.audit.spring3;

import io.github.jasper.monitoring.api.MonitorActionEnricher;
import io.github.jasper.monitoring.api.MonitorActionFacts;
import io.github.jasper.monitoring.api.MonitorActionInvocation;
import io.github.jasper.monitoring.api.SecurityEventResult;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

/**
 * Collects approved export facts from the controller result.
 * The dynamic sensitivity=LOW attribute intentionally conflicts with static HIGH to prove static precedence.
 */
@Component
public class AuditExportFacts implements MonitorActionEnricher {
    @Override
    public MonitorActionFacts enrich(MonitorActionInvocation invocation) {
        if (invocation.getPhase() != MonitorActionInvocation.Phase.AFTER_RETURNING) {
            return MonitorActionFacts.empty();
        }
        Object result = invocation.getReturnValue();
        if (result instanceof ResponseEntity) {
            result = ((ResponseEntity<?>) result).getBody();
        }
        if (!(result instanceof Map)) {
            return MonitorActionFacts.empty();
        }
        Object rowCount = ((Map<?, ?>) result).get("rowCount");
        if (!(rowCount instanceof Number)) {
            return MonitorActionFacts.empty();
        }
        return MonitorActionFacts.builder().dataCount(((Number) rowCount).longValue())
            .result(SecurityEventResult.SUCCESS).reasonCode("EXPORT_COMPLETED")
            .attribute("sensitivity", "LOW").build();
    }
}

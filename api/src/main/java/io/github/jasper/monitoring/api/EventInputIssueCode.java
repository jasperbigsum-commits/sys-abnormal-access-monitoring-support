package io.github.jasper.monitoring.api;

import io.github.jasper.monitoring.api.code.CodeFamily;
import io.github.jasper.monitoring.api.code.GovernedCode;

/**
 * 可持久化的受控输入质量问题码。
 */
public enum EventInputIssueCode implements GovernedCode {
    MISSING_DATA_COUNT,
    MISSING_LATENCY_MS,
    MISSING_RESOURCE_ID,
    MISSING_ORG_SCOPE,
    MISSING_SOURCE_IP,
    MISSING_TARGET_USER_ID,
    MISSING_REASON_CODE,
    MISSING_FACT,
    INVALID_FACT,
    INVALID_SOURCE_IP,
    INVALID_PARAMETER_VALUE,
    UNRESOLVED_PARAMETER_PATH,
    PROTECTED_FACT_OVERRIDE,
    EVENT_ENRICHER_REJECTED;

    @Override
    public String getCode() {
        return "MON.INPUT." + name();
    }

    @Override
    public CodeFamily getFamily() {
        return CodeFamily.INPUT_DIAGNOSTIC;
    }
}

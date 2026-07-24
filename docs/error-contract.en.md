# Public Error Contract

[Back to the English README](../README.en.md)

The framework-neutral contract lives in `io.github.jasper.monitoring.api.error`. Codes are stable machine-readable values. Messages are safe diagnostics only: hosts must not parse them or expose exception causes to external clients.

## Exception hierarchy

Every public failure implements `MonitoringFailure`. `MonitoringValidationException` extends `IllegalArgumentException`; `MonitoringConfigurationException` and `MonitoringStateException` extend `IllegalStateException`; `MonitoringPersistenceException` extends `RuntimeException`. Existing broad catches therefore remain compatible.

## Codes

| Code | Enum name | Exception | Retry guidance | Host responsibility |
| --- | --- | --- | --- | --- |
| `MON-001` | `REQUIRED_FIELD_MISSING` | Validation | Do not retry unchanged. | Supply the required safe field. |
| `MON-002` | `INVALID_FIELD_VALUE` | Validation | Do not retry unchanged. | Correct the format, range, or path. |
| `MON-003` | `UNSAFE_EVENT_ATTRIBUTE` | Validation | Do not retry unchanged. | Remove sensitive or unsupported attributes. |
| `MON-101` | `ACTION_NOT_REGISTERED` | Validation | Retry after configuration repair. | Register the static action before recording. |
| `MON-102` | `CONFLICTING_ACTION_DEFINITION` | Configuration | Do not retry automatically. | Make every definition for the action identical. |
| `MON-103` | `RULE_REGISTRY_FROZEN` | State | Do not retry automatically. | Register rules before monitor construction. |
| `MON-104` | `DUPLICATE_CONTROL_BINDING` | Configuration | Do not retry automatically. | Keep one host binding per action. |
| `MON-105` | `INVALID_CONTROL_TRIGGER` | Validation | Retry after a code fix. | Correct visibility, parameter, return type, or action. |
| `MON-106` | `DUPLICATE_INTERNAL_RULE_ID` | Configuration | Do not retry automatically. | Keep one implementation per rule ID. |
| `MON-201` | `ALERT_NOT_FOUND` | Validation | Refresh the identifier first. | Reload the alert and validate its ID. |
| `MON-202` | `INVALID_ALERT_TRANSITION` | State | Reload state before deciding. | Select a transition allowed by current state. |
| `MON-301` | `ENFORCEMENT_HANDLER_REQUIRED` | Configuration | Do not retry automatically. | Register and validate a real handler before `ENFORCE`. |
| `MON-401` | `PERSISTENCE_OPERATION_FAILED` | Persistence | Retry only under an idempotent policy. | Check migration, connectivity, permissions, and database health; retain the cause internally. |

Assigned codes are never renamed or reused; minor releases may add codes. A monitoring code is not an HTTP status, RPC status, or broker acknowledgement policy. The host transport boundary owns that mapping.

## Host inspection

```java
try {
    monitor.record(draft);
} catch (RuntimeException exception) {
    if (!(exception instanceof MonitoringFailure)) {
        throw exception;
    }
    MonitoringErrorCode code = ((MonitoringFailure) exception).getErrorCode();
    // The host maps code to its own HTTP, RPC, or message response contract.
    auditLogger.warn("monitoring_failure_code={}", code.getCode());
}
```

Log only the code and approved stable identifiers. Never log credentials, raw payloads, SQL text, or return database causes to a client. Unexpected `RuntimeException` values are deliberately not converted into a generic monitoring failure because that would hide programming defects.

Related: [Integration Guide](integration-guide.en.md), [Architecture and Transaction Boundaries](architecture-and-transaction-boundaries.en.md), and [Roadmap](roadmap.en.md).

# Architecture and Operations

The dependency direction is `starter -> spring-support/core/mybatis -> api`, while `core` remains free of Spring, Servlet, and MyBatis. The host owns identity, authorization, endpoints, and control side effects.

`MonitoringService` resolves a frozen action, validates fact values and sources, assembles the immutable event, persists it, and invokes typed rule evaluation. The evaluator replaces a database round-tripped copy of the current event with the original event ID instance so timestamp precision cannot exclude it from the active window.

`MonitoringTransaction.required(...)` commits history-dependent alert changes and event links in one MyBatis session. Notifications and host control effects occur after that commit. Durable control reservation, approval, execution, retry, version changes, and attempts are persisted separately. Management mutations combine state transitions and append-only `management_audit` records.

Startup fails when MyBatis is unavailable, the action catalog is invalid or unfrozen, fact bindings are invalid, or ENFORCE lacks any built-in rule control handler. Default skip handlers never count as executable enforcement.

The component does not join the host business transaction. Use a transactional outbox or an explicitly approved adapter when cross-resource atomicity is required.

Before production, run the full reactor, test the target database and migration path, validate all six control types and idempotent replay, verify management allow/deny audit, and exercise database/control failures. Never replace unavailable persistence with local memory state.

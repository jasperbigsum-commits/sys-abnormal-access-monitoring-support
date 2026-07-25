# Integration Guide

[Back to the English README](../README.en.md)

This guide is for host application developers. Keep authentication, authorization, trusted network interpretation, and real control behavior in the host. The component records and evaluates evidence; it does not replace those decisions.

## Fifteen-minute path

1. Import exactly one starter matching the host: `spring2-starter` for Boot 2.7 and `spring3-starter` for Boot 3.
2. Apply `mybatis/src/main/resources/db/monitoring-schema.sql` through the host's controlled migration process.
3. Implement server-side identity and authorization SPIs, plus trusted proxy resolution.
4. Configure a stable `system-id`, `OBSERVE`, and the smallest valid trusted-proxy ranges.
5. Submit one synthetic event containing no credentials or raw payloads and verify its database rows.
6. Register a real `ControlHandler` or valid `@ControlTrigger`, then test success, failure, timeout, and idempotent replay.
7. Enable `ENFORCE` only after the preceding evidence is available.

```yaml
abnormal:
  access:
    monitor:
      system-id: order-service
      mode: OBSERVE
      trusted-proxies: [10.0.0.0/8]
      frontend:
        enabled: true
      instrumentation:
        enabled: true
      mdc:
        enabled: true
        trace-id-key: traceId
```

With a configured `SqlSessionFactory`, the starter creates `MyBatisMonitoringRepository`. Without one it uses the in-memory repository, which is suitable only for local development and tests. The adapter supports compatible MyBatis 3.5.x core versions and does not force a MyBatis Boot Starter.

## Required host boundaries

Implement and register `IdentityContextProvider`, `ResourceScopeAuthorizer`, and `TrustedProxyResolver`. `ResourceAccessGuard` records the host decision and defaults to denial when no valid authorization result exists. Client-supplied identity, role, source IP, session, or authorization results are never authoritative.

The host also owns the frontend endpoint. Authenticate it, limit request size and rate, validate `frontend-signal.schema.json`, construct `FrontendServerContext` from trusted server data, and call `FrontendSignalRecorder.record(...)`. Browser telemetry is supplementary evidence only.

## Common omissions

| Problem | Observable risk | Required correction |
| --- | --- | --- |
| Schema was not applied | `MON-401` on the first persistent operation | Run the controlled migration before enabling persistence. |
| In-memory fallback reaches production | Audit data disappears on restart and does not aggregate | Require a real `SqlSessionFactory` and verify repository type at startup. |
| Both starters are present | Conflicting servlet and auto-configuration generations | Keep exactly one starter matching the Boot major. |
| Anonymous identity remains in production | Events cannot support accountable investigation | Supply verified server-side identity. |
| Forwarding headers are trusted from every peer | Source IP can be spoofed | Configure only real proxy CIDRs. |
| `ENFORCE` is enabled without a tested handler | Startup fails or controls cannot execute | Validate a real handler first; fallbacks do not count. |
| A manual action was never registered | Recording fails with `MON-101` | Register one stable `MonitorActionDefinition`. |
| Sensitive event properties are added | Validation fails or confidential data enters audit storage | Use approved non-sensitive identifiers and reason codes only. |
| Frontend evidence is treated as authoritative | Identity or authorization can be forged | Derive authority from server-side context. |
| A database rule version is assumed active | Management state differs from runtime behavior | Use an approved loader; current runtime rules come from the frozen internal registry. |

## Action instrumentation

Use `@MonitorAction` for completed Servlet MVC handler observations. It stores static action metadata only and does not inspect parameters, bodies, responses, or exception text. Method annotations override type annotations.

For services, message consumers, and jobs, register `MonitorActionDefinition` values in `MonitoringActionRegistry` and use `ActionEventRecorder.draft(...)` or `record(...)`. This gives MVC and non-MVC entry points one action vocabulary. Dynamic resource IDs, counts, latency, and approved reason codes belong in each invocation draft.

## Controls and authorization

Implement `ControlHandler` directly or annotate a public Spring bean method with `@ControlTrigger`. A trigger accepts exactly one `ControlCommand` and returns `void` or `ControlExecution`. There can be only one annotated binding per action. Handlers must honor the idempotency key and return a safe result without exposing credentials or payloads.

Call `ResourceAccessGuard` before protected business work. Monitoring records the authorization outcome but cannot elevate a denial. Post-completion action instrumentation is audit evidence, not a substitute for pre-operation authorization or control.

## Rules, notifications, and tracing

Register deterministic, side-effect-free `DetectionRule` implementations before `InternalRuleRegistry` is frozen. Persisted rule versions are queryable management data; they do not become runtime rules automatically. Any dynamic loader must validate definitions, require approval, stage rollout, invalidate caches safely, and retain rollback evidence.

Provide a `NotificationChannel` for host delivery. Notification and control execution happen after monitoring data commits. For reliable external delivery, use the host's approved outbox strategy rather than joining irreversible effects to the monitoring transaction.

The request adapters propagate an inbound trace ID, reuse the configured MDC value, or create an ID in that order, then restore the previous MDC on completion. Message and job hosts must propagate MDC and set the same trace ID on `MonitoringRequestContext` themselves.

## Persistence and transactions

`MonitoringRepository.inTransaction(...)` commits the event, rule-derived alert changes, and event links as one monitoring unit. Alert lifecycle transitions commit the alert status and append-only disposition together. Nested MyBatis repository calls join one managed session. MyBatis failures become `MON-401` while retaining the original cause for internal diagnostics.

The monitoring transaction does not join the host business transaction. Use a transactional outbox or an explicitly approved host adapter when both resources must be atomic. See [Architecture and Transaction Boundaries](architecture-and-transaction-boundaries.en.md).

## Production acceptance

- A safe synthetic event is persisted with the expected `system_id`, request ID, trace ID, and server-derived identity.
- Missing schema, unavailable database, and rollback paths are observable without leaking causes to clients.
- Authorization defaults to deny; untrusted forwarding headers cannot change source IP.
- Every enabled rule has threshold and false-positive evidence; every control action has idempotency and failure tests.
- Alert acknowledgment, investigation, closure, and false-positive decisions retain append-only operator evidence.
- `mvn clean verify -DskipTests=false` passes for the selected revision.

Errors are documented in the [Public Error Contract](error-contract.en.md). Planned operational work is tracked in the [Roadmap](roadmap.en.md).

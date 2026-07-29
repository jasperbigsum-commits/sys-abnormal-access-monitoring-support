# Abnormal Access Monitoring Support

A strict, typed monitoring component for Spring Boot 2.1/2.7 and Boot 3 hosts. The host remains authoritative for identity, resource authorization, sessions, endpoints, and real control effects. This component validates action facts, evaluates 14 built-in rules, persists alerts and durable controls through MyBatis, and exposes controller-ready management services.

Production persistence is MyBatis-only. There is no in-memory repository fallback and no automatic DDL execution. Start in `OBSERVE`; `ENFORCE` refuses startup unless executable handlers cover every control type emitted by enabled built-in rules.

## Modules

| Module | Responsibility |
| --- | --- |
| `api` | Typed Action, Fact, Rule, Control, and management contracts |
| `core` | Event assembly, rule evaluation, alerts, durable controls, management use cases |
| `mybatis` | The production persistence adapter, mappers, and schema |
| `web-contract` | Supplemental browser signal schema |
| `spring-support` | Shared Spring adapters |
| `spring2-legacy-starter` / `spring2-starter` / `spring3-starter` | Boot 2.1 / 2.7 / 3 auto-configuration |
| `integration-audit` | Real Boot 2/3 HTTP and MyBatis acceptance hosts |

## Core Boundaries

- Actions are concrete `ActionType` classes, not free-form strings.
- `ActionCatalog` owns static semantics and must be frozen before runtime creation.
- Facts retain their Java value type through `FactType<T>`.
- `FactBinding`, not the provider, owns provider applicability to an Action or Action Contract.
- Fact sources are preserved per fact and checked by both Action and Rule definitions.
- `MonitoringService` is the programmatic entry point; typed `@MonitorAction` is an MVC adapter.
- Management services bind a trusted `ManagementAuthorizer` at construction time.
- Control execution is a durable, versioned, idempotent state machine.

## Start

1. Import the BOM and one starter matching the host Boot version (`spring2-legacy-starter` for Boot 2.1, `spring2-starter` for Boot 2.7, `spring3-starter` for Boot 3).
2. Apply `mybatis/src/main/resources/db/monitoring-schema.sql` through the host migration process.
3. Provide a working `SqlSessionFactory` and trusted identity/authorization adapters.
4. Run in `OBSERVE` and verify events, alerts, management authorization, and audit rows.
5. Register real idempotent handlers for all required built-in controls before enabling `ENFORCE`.

```yaml
abnormal:
  access:
    monitor:
      system-id: order-service
      mode: OBSERVE
      instrumentation:
        enabled: true
      trusted-proxies: [10.0.0.0/8]
```

```java
monitoringService.monitor(ActionExecution.of(
    BuiltInActions.ReportExport.class,
    requestContext,
    identityContext,
    ActionOutcome.success(latencyMs),
    ActionFacts.builder()
        .put(BuiltInFacts.ResourceId.class, reportId)
        .put(BuiltInFacts.DataCount.class, exportedRows)
        .build(),
    FactSource.HOST_PROVIDER));
```

When a host supplies `ManagementAuthorizer`, the starters expose `SecurityEventQueryService`, `AlertManagementService`, `RuleCatalogService`, `WhitelistManagementService`, and `ControlManagementService`. The host maps these services to its own controllers and frontend; authorization, optimistic locking, transactions, and `monitoring_management_audit` remain inside the services.

```bash
mvn clean verify -DskipTests=false
```

Never record passwords, tokens, cookies, keys, or unapproved raw payloads. Browser telemetry is supplemental only; server-side identity and authorization remain authoritative.

See the [integration guide](docs/integration-guide.en.md) and [architecture and operations](docs/architecture-and-transaction-boundaries.en.md).

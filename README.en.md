# Abnormal Access Monitoring and Control Support

This Maven reactor provides reusable abnormal-access monitoring for self-hosted Spring Boot systems. It records normalized security events, evaluates deterministic rules, maintains alert history, delegates controls to the host application, and persists an auditable trail without taking ownership of host authentication or authorization.

Spring Boot 2.7.x (`javax.servlet`) and Spring Boot 3.x (`jakarta.servlet`) are supported by separate starters. “Spring 2/3” means the Boot generation, not legacy Spring Framework 2.x/3.x.

## Modules

| Module | Purpose |
| --- | --- |
| `api` | Framework-neutral contracts, event drafts, and host SPIs. |
| `core` | Domain model, rules, application services, and ports. |
| `web-contract` | Browser signal model, validation, and JSON Schema. |
| `mybatis` | MyBatis persistence adapter, annotated mappers, and schema migration. |
| `spring-support` | Shared Spring integration helpers. |
| `spring2-starter` / `spring3-starter` | Boot-specific auto-configuration. |
| `maven-plugin` | Safe initialization templates. |
| `bom` | Version management for the published modules. |

## Quick start

Production adoption follows seven evidence-based steps: one matching starter, controlled schema migration, trusted identity/authorization/proxy SPIs, `OBSERVE`, a safe test event, a tested real control handler, and only then `ENFORCE`. See the [Integration Guide](docs/integration-guide.en.md#fifteen-minute-path) for commands, omissions, and advanced features.

Import the BOM, then add exactly one starter matching the host's Boot major version:

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>io.github.jasperbigsum-commits</groupId>
            <artifactId>sys-abnormal-access-monitoring-bom</artifactId>
            <version>${abnormal-access-monitoring.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>

<dependency>
    <groupId>io.github.jasperbigsum-commits</groupId>
    <artifactId>sys-abnormal-access-monitoring-spring3-starter</artifactId>
</dependency>
```

Apply the controlled database migration at `mybatis/src/main/resources/db/monitoring-schema.sql`, provide the required host SPIs, and start in observation mode:

```yaml
abnormal:
  access:
    monitor:
      system-id: order-service
      mode: OBSERVE
      frontend:
        enabled: true
```

Implement and register `IdentityContextProvider`, `ResourceScopeAuthorizer`, and `TrustedProxyResolver`. The component records authorization conclusions but never promotes a denied request to allowed. Browser telemetry is supplementary evidence only; trusted identity, authorization, source IP, and session data must be established server-side.

## Architecture and transactions

The domain is isolated under `core.domain`; pure deterministic rules live in `core.domain.rule`; use cases are in `core.application`; replaceable integrations are declared in `core.port`. MyBatis, Spring, Servlet, and database details are outer adapters and must not leak back into domain rules.

`MonitoringRepository.inTransaction(...)` commits an event, its evaluated alert state, and alert links atomically. Alert status changes and append-only dispositions use the same guarantee. Notifications and host controls execute after commit because those external operations cannot be safely rolled back. The monitoring transaction is separate from the host business transaction; use an outbox or an approved host adapter for a cross-resource atomic workflow.

The starters do not force a MyBatis Boot Starter version. The adapter is compiled against MyBatis `3.5.19` and uses stable 3.5.x core APIs, so a host can retain its compatible MyBatis integration and expose a `SqlSessionFactory`.

Within the MyBatis adapter, the root package retains the repository, registrar, mappers, and the single `InstantTypeHandler`; `mybatis.po` contains database-row mappings and the rule-version query projection. POs never leak into `api` or replace immutable `core.domain` objects. The repository is the only domain-to-PO conversion boundary.

## Operational safety

Keep `ENFORCE` disabled until at least one host `ControlHandler`, thresholds, and failure paths have been validated. The starter rejects `ENFORCE` when no executable host handler is present. Never record passwords, tokens, cookies, secrets, raw request bodies, or raw response bodies.

Build and verify all modules with:

```bash
mvn clean verify -DskipTests=false
```

## Documentation

- [Integration Guide](docs/integration-guide.en.md): fifteen-minute adoption, common omissions, advanced integration, and production acceptance.
- [Public Error Contract](docs/error-contract.en.md): all 13 stable codes, hierarchy, retry guidance, and host transport mapping.
- [Architecture and Transaction Boundaries](docs/architecture-and-transaction-boundaries.en.md): module isolation, runtime flow, persistence atomicity, and post-commit effects.
- [Feature and Optimization Roadmap](docs/roadmap.en.md): M0-M3 priorities, completion signals, and explicitly deferred work.
- [Chinese README](README.md): the Chinese documentation index.

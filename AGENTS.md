# Repository Guidelines

## Project Structure & Module Organization

This is a Maven reactor. `api/` contains framework-neutral contracts and models; `core/` contains rule evaluation, alerts, controls, and authorization guards. `web-contract/` defines frontend signal validation and the JSON schema. `mybatis/` owns the repository implementation and `src/main/resources/db/monitoring-schema.sql`. `spring-support/`, `spring2-starter/`, and `spring3-starter/` provide shared and Boot-specific auto-configuration. `maven-plugin/` owns the `initialize` goal; `bom/` publishes managed dependency versions. Place production code under `src/main/java` and tests under `src/test/java` in the owning module.

## Build, Test, and Development Commands

- `mvn clean verify -DskipTests=false` builds all modules and runs the full test suite. Run this before submitting changes.
- `mvn -pl core -am -Dtest=DefaultSecurityMonitorTest test` runs a focused module test with required upstream modules.
- `mvn -pl mybatis -am test` validates mapper behavior against H2.
- `mvn -pl maven-plugin package` packages and tests the Maven plugin.
- `mvn install` is useful only when manually consuming the snapshot from the local Maven repository.

## Coding Style & Naming Conventions

Use Java with four-space indentation, braces on the declaration line, and standard Java naming: `PascalCase` classes, `camelCase` methods and fields, and `UPPER_SNAKE_CASE` enum constants. Keep common modules Java 8 compatible; do not introduce `jakarta.*` into Boot 2 modules or `javax.*` into Boot 3 modules. Keep integrations behind interfaces in `api/`; do not couple core logic to Spring Security, servlet APIs, or application-specific authorization frameworks. No formatter is configured, so match adjacent code and keep diffs focused.

## Testing Guidelines

Tests use JUnit 5, AssertJ for Spring context assertions, Mockito where needed, and H2 for MyBatis integration tests. Name test classes `*Test` and test methods after observable behavior, for example `deniesResourceAccessWhenTheHostAuthorizerIsNotConfigured`. Add or update tests with every behavior change, especially rule thresholds, control TTLs, persistence mappings, and Boot 2/3 parity.

## Commit & Pull Request Guidelines

Until enough Git history exists to establish local conventions, use Conventional Commit-style messages such as `feat(core): add IP rate-limit target` or `fix(mybatis): persist alert disposition`. Pull requests should explain the affected modules, security behavior, migration impact, and commands run. Include schema migration notes when changing `monitoring-schema.sql`.

## Security & Configuration

Never add passwords, tokens, cookies, raw sensitive payloads, or production credentials to events, examples, or tests. Keep `ENFORCE` disabled until a host `ControlHandler` is registered and validated. Treat frontend telemetry as supplemental evidence only; server-side identity and authorization remain authoritative.

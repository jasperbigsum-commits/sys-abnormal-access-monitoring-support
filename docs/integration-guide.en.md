# Integration Guide

This guide is for host and management API developers.

## Installation

Use exactly one starter (`spring2-legacy-starter` for Boot 2.1, `spring2-starter` for Boot 2.7, `spring3-starter` for Boot 3), apply the monitoring schema through controlled migrations, provide a `SqlSessionFactory`, and start in `OBSERVE`. Missing MyBatis persistence is a startup error; H2 and memory fixtures are test-only.

## Typed Actions and Facts

Built-in and custom actions use the same `ActionType` system. A custom action must be concrete and final, registered once in `ActionCatalog`, and associated with a complete `ActionDefinition`. Use an `ActionContract` only for an explicit shared capability.

`ActionFactProvider` produces facts. `FactBinding.forAction(...)` or `forContract(...)` owns applicability. Providers may return only declared facts. Duplicate fact producers, value type mismatches, and unapproved sources fail before rule evaluation.

Use `MonitoringService.monitor(ActionExecution)` for services, jobs, and consumers. Typed `@MonitorAction(BuiltInActions.Query.class)` is available for fixed MVC actions, but it does not inspect parameters or payloads and does not replace business authorization.

## Enforcement

`ENFORCE` requires executable handlers for `REQUIRE_CAPTCHA`, `RATE_LIMIT`, `REVOKE_SESSION`, `REQUIRE_MFA`, `DENY`, and `REQUIRE_APPROVAL`. Framework fallback handlers do not satisfy this check. Every host handler must be idempotent by command key and validate rule, subject, and expiry.

## Management Controllers

Provide one trusted `ManagementAuthorizer` bean. The starters then expose five management service beans. Controllers must create `ManagementActor` from authenticated server context, never from request-controlled actor or system fields. The services enforce scope, optimistic versions, transactions, and append success or denial to `monitoring_management_audit`.

## Browser Signals

The host owns the HTTP endpoint, authentication, body limits, rate limiting, and response model. Validate the JSON Schema, attach trusted server context, then call `FrontendSignalRecorder`. The frontend signal action has no built-in rule bindings and cannot produce built-in controls.

## Acceptance

Verify missing persistence fails startup, unknown actions and invalid facts fail early, MyBatis contains event/alert/control/audit rows, cross-system management access is denied and audited, timestamp round trips do not remove the current window event, and Boot 2/3 behavior is symmetric.

Run `mvn clean verify -DskipTests=false` before delivery.

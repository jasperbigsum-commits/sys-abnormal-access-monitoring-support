# Resource Scope Resolution Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Resolve trusted organization and host-specific resource facts once before authorization, then reuse the same fact snapshot for authorization, pass lookup, security-event recording, and monitored-action evaluation.

**Architecture:** Add a framework-neutral `ResourceScopeResolver` contract in `api`. The existing single typed action advisor invokes it through `ResourceAccessStage`; the stage merges only non-conflicting typed facts, fails closed when an annotated action requires an unresolved organization scope, delegates the final decision to `ResourceAccessGuard`, and returns resolver facts to the normal monitoring pipeline. Host-specific dimensions remain custom `FactType` values, while `orgScope` remains the standard primary organization/data-domain boundary.

**Tech Stack:** Java 8-compatible API/core/spring-support modules, Spring Boot 2/3 auto-configuration, Apache Shiro integration fixtures, JUnit 5, Maven reactor.

---

### Task 1: Define the resolver contract

**Files:**
- Create: `api/src/main/java/io/github/jasper/monitoring/api/ResourceScopeResolver.java`
- Create: `api/src/main/java/io/github/jasper/monitoring/api/ResourceScopeResolveRequest.java`
- Create: `api/src/main/java/io/github/jasper/monitoring/api/ResourceScopeResolution.java`
- Modify: `api/src/main/java/io/github/jasper/monitoring/api/action/ResourceAccess.java`
- Test: `api/src/test/java/io/github/jasper/monitoring/api/ResourceScopeResolutionTest.java`

- [ ] **Step 1: Write the failing API tests**

Cover immutable request metadata, an explicit unresolved result, typed resolved facts, and rejection of null inputs.

- [ ] **Step 2: Run the API test and verify RED**

Run: `mvn -pl api -Dtest=ResourceScopeResolutionTest test`

Expected: compilation fails because the resolver contract types do not exist.

- [ ] **Step 3: Add the minimal framework-neutral types**

Define `ResourceScopeResolver.resolve(ResourceScopeResolveRequest)`, an immutable request containing request/identity/action/resource/facts, and an immutable result containing resolver-produced `ActionFacts`. Add `requireOrgScope` to `@ResourceAccess` with a default of `false`.

- [ ] **Step 4: Run the API test and verify GREEN**

Run: `mvn -pl api -Dtest=ResourceScopeResolutionTest test`

Expected: all `ResourceScopeResolutionTest` tests pass.

### Task 2: Resolve once and fail closed in the resource stage

**Files:**
- Modify: `spring-support/src/main/java/io/github/jasper/monitoring/spring/support/ResourceAccessStage.java`
- Modify: `api/src/main/java/io/github/jasper/monitoring/api/ResourceScopeRequest.java`
- Modify: `core/src/main/java/io/github/jasper/monitoring/core/application/authorization/ResourceAccessGuard.java`
- Create: `spring-support/src/test/java/io/github/jasper/monitoring/spring/support/ResourceAccessStageTest.java`
- Modify: `core/src/test/java/io/github/jasper/monitoring/core/ResourceAccessGuardTest.java`

- [ ] **Step 1: Write failing stage and guard tests**

Cover resolver invocation exactly once, resolver `OrgScope` propagation, explicit `OrgScope` avoiding a resource lookup, conflicting fact rejection, required-but-unresolved scope denial, resolver failure denial, and denial-event persistence.

- [ ] **Step 2: Run focused tests and verify RED**

Run: `mvn -pl spring-support,core -am -Dtest=ResourceAccessStageTest,ResourceAccessGuardTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: compilation or assertions fail because the stage has no resolver and returns no reusable facts.

- [ ] **Step 3: Implement the single-resolution stage**

Cache request and identity once, call the resolver only when the initial snapshot has no `OrgScope`, reject duplicate resolver fact types, pass the resolved scope and requirement flag into the guard, and return only newly resolved facts for downstream merging.

- [ ] **Step 4: Implement guard failures for required organization scope**

Treat missing required `OrgScope` as a normal failed-closed authorization decision so the existing access-denied recorder remains the single audit path. Resolver exceptions are converted to an evaluation-error request and cannot be overridden by a pass.

- [ ] **Step 5: Run focused tests and verify GREEN**

Run: `mvn -pl spring-support,core -am -Dtest=ResourceAccessStageTest,ResourceAccessGuardTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: all focused tests pass.

### Task 3: Reuse resolver facts in all typed action advisors

**Files:**
- Modify: `spring2-starter/src/main/java/io/github/jasper/monitoring/spring2/autoconfigure/TypedMonitorActionAspect.java`
- Modify: `spring3-starter/src/main/java/io/github/jasper/monitoring/spring3/autoconfigure/TypedMonitorActionAspect.java`
- Modify: `spring2-legacy-starter/src/main/java/io/github/jasper/monitoring/spring2/autoconfigure/TypedMonitorActionAspect.java`
- Modify: corresponding `TypedMonitorActionAspectTest.java` files in all three starter modules
- Modify: `api/src/main/java/io/github/jasper/monitoring/api/action/BuiltInActions.java`

- [ ] **Step 1: Add failing advisor reuse tests**

Assert that a resolver-supplied `OrgScope` reaches the final monitored event and the resolver is invoked once for one intercepted method call.

- [ ] **Step 2: Run the three advisor tests and verify RED**

Run each starter module with `-Dtest=TypedMonitorActionAspectTest -Dsurefire.failIfNoSpecifiedTests=false`.

Expected: the final event lacks resolver facts or construction fails for the new stage signature.

- [ ] **Step 3: Merge resolved facts as `HOST_PROVIDER` evidence**

Carry the stage result through checkpoint and final monitoring paths, preserving duplicate-source rejection. Permit `OrgScope` from `HOST_PROVIDER` on built-in resource actions that can use `@ResourceAccess`.

- [ ] **Step 4: Run the advisor tests and verify GREEN**

Expected: Boot 2, Boot 3, and legacy Boot 2 tests pass with identical behavior.

### Task 4: Auto-configure a resolver in all starters

**Files:**
- Modify: all three `AbnormalAccessMonitorAutoConfiguration.java` files
- Modify: starter auto-configuration tests where bean fallback/override behavior is asserted

- [ ] **Step 1: Add failing context tests**

Assert a default unresolved resolver exists and a host `ResourceScopeResolver` replaces it.

- [ ] **Step 2: Run starter context tests and verify RED**

Expected: no resolver bean exists and the instrumentation stage cannot receive one.

- [ ] **Step 3: Add `@ConditionalOnMissingBean` fallback and inject it**

The fallback returns `ResourceScopeResolution.unresolved()`; it must never derive scope from client input, URL shape, or the current user.

- [ ] **Step 4: Run starter context tests and verify GREEN**

Expected: all three starters expose overrideable resolver beans.

### Task 5: Demonstrate trusted Shiro host integration

**Files:**
- Modify: `integration-audit/spring2-web/src/main/java/io/github/jasper/monitoring/audit/spring2/security/AuditShiroRbacConfiguration.java`
- Modify: `integration-audit/spring3-web/src/main/java/io/github/jasper/monitoring/audit/spring3/security/AuditShiroRbacConfiguration.java`
- Modify: both `ReportExportService.java` files
- Modify: relevant integration tests and READMEs in both audit applications

- [ ] **Step 1: Add failing integration assertions**

Assert export authorization resolves report ownership server-side, persists `org_scope` on access and export events, denies missing reports, and does not re-read the report catalog in the authorizer.

- [ ] **Step 2: Run audit integration tests and verify RED**

Expected: no resolver bean exists and `org_scope` is absent from the monitored export event.

- [ ] **Step 3: Add host resolvers and simplify authorizers**

Each resolver loads `AuditReportCatalog` once and emits `BuiltInFacts.OrgScope`; each Shiro authorizer consumes that resolved scope plus trusted identity/permission without repeating the catalog query. Mark report export access as requiring organization scope.

- [ ] **Step 4: Run audit integration tests and verify GREEN**

Expected: both Boot generations pass the same ownership, permission, denial, and persistence assertions.

### Task 6: Reactor verification

**Files:**
- Verify all modified files

- [ ] **Step 1: Run focused module tests**

Run: `mvn -pl api,core,spring-support,spring2-starter,spring3-starter,spring2-legacy-starter -am test`

- [ ] **Step 2: Run the full reactor**

Run: `mvn clean verify -DskipTests=false`

Expected: all reactor modules and integration tests pass.

- [ ] **Step 3: Check patch hygiene**

Run: `git diff --check`

Expected: no whitespace errors; existing unrelated working-tree changes remain intact.

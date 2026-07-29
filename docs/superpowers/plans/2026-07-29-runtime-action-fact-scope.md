# Runtime Action Fact Scope Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a safe runtime Fact scope for `@MonitorAction` methods, preserve per-Fact provenance, and provide a concise injected recording alternative.

**Architecture:** A Java 8 compatible `ThreadLocal<Deque<Scope>>` in `spring-support` isolates runtime facts per synchronous intercepted invocation. Starter aspects combine parameter and runtime contributions into an `ActionExecution` whose supplied facts carry per-item sources; `core` validates and persists those sources. A `MonitoringRecorder` bean provides a concise explicit path for synchronous Servlet request code.

**Tech Stack:** Java 8, Maven reactor, Spring AOP, Spring Boot 2/3 auto-configuration, JUnit 5, H2/MyBatis integration audit.

---

## File Structure

- Create `api/src/test/java/io/github/jasper/monitoring/api/event/ActionExecutionTest.java`: compatibility and per-Fact source contract.
- Modify `api/src/main/java/io/github/jasper/monitoring/api/event/ActionExecution.java`: immutable per-Fact source mapping and compatible factories.
- Modify `core/src/test/java/io/github/jasper/monitoring/core/DefaultMonitoringRuntimeTest.java`: verify collection and persisted provenance.
- Modify `core/src/main/java/io/github/jasper/monitoring/core/application/DefaultMonitoringRuntime.java`: consume per-Fact sources.
- Create `spring-support/src/test/java/io/github/jasper/monitoring/spring/support/MonitoringFactsTest.java`: scope, nesting, concurrency, conflicts, and cleanup.
- Create `spring-support/src/main/java/io/github/jasper/monitoring/spring/support/MonitoringFacts.java`: business-facing static `put` API.
- Create `spring-support/src/main/java/io/github/jasper/monitoring/spring/support/MonitoringFactScope.java`: starter-facing lifecycle and immutable snapshot.
- Create `spring-support/src/test/java/io/github/jasper/monitoring/spring/support/MonitoringRecorderTest.java`: concise explicit recording behavior.
- Create `spring-support/src/main/java/io/github/jasper/monitoring/spring/support/MonitoringRecorder.java`: context-aware injected facade.
- Modify all three `TypedMonitorActionAspectTest.java` and `TypedMonitorActionAspect.java`: open, consume, merge, and close scopes.
- Modify all three `AbnormalAccessMonitorAutoConfiguration.java` and tests: expose `MonitoringRecorder` in Servlet applications.
- Create Spring 2/3 `AnnotatedMonitoringService.java`: ordinary Spring Service annotation example.
- Modify Spring 2/3 `MonitoringFixtureController.java` and `Spring2/3AuditWebAcceptanceTest.java`: route IA-04 through the Service and verify `HOST_PROVIDER`.
- Create `docs/注解监听与运行时事实作用域.md`: standalone architecture and usage guide.
- Modify `README.md`, `docs/集成指南.md`, and Spring 2/3 integration audit README files: link the standalone guide.

### Task 1: Per-Fact Supplied Sources

**Files:**
- Create: `api/src/test/java/io/github/jasper/monitoring/api/event/ActionExecutionTest.java`
- Modify: `api/src/main/java/io/github/jasper/monitoring/api/event/ActionExecution.java`
- Test: `api/src/test/java/io/github/jasper/monitoring/api/event/ActionExecutionTest.java`

- [ ] **Step 1: Write failing API tests**

Add tests that construct two facts with distinct sources and assert:

```java
assertEquals(FactSource.METHOD_PARAMETER,
    execution.getSuppliedFactSources().get(ResourceFact.class));
assertEquals(FactSource.HOST_PROVIDER,
    execution.getSuppliedFactSources().get(DataCountFact.class));
```

Also assert the existing single-source factory expands its source across every supplied Fact, and reject missing or extra source-map keys.

- [ ] **Step 2: Verify the tests fail for the missing API**

Run: `mvn -pl api -Dtest=ActionExecutionTest test`

Expected: compilation failure because `getSuppliedFactSources()` and the map-based factory do not exist.

- [ ] **Step 3: Implement the compatible immutable source contract**

Add:

```java
default Map<Class<? extends FactType<?>>, FactSource> getSuppliedFactSources() {
    Map<Class<? extends FactType<?>>, FactSource> result =
        new LinkedHashMap<Class<? extends FactType<?>>, FactSource>();
    for (Class<? extends FactType<?>> type : getSuppliedFacts().asMap().keySet()) {
        result.put(type, getSuppliedFactSource());
    }
    return Collections.unmodifiableMap(result);
}
```

Add a map-based `of` overload, validate exact key equality and non-null sources, and store an immutable copy in `ImmutableActionExecution`. Retain `getSuppliedFactSource()` for source compatibility; map-based mixed-source executions must not use it in the runtime path.

- [ ] **Step 4: Run API tests**

Run: `mvn -pl api -Dtest=ActionExecutionTest test`

Expected: all `ActionExecutionTest` tests pass.

- [ ] **Step 5: Commit the API increment**

```text
feat(api): support per-fact supplied sources
```

### Task 2: Core Collection Uses Per-Fact Sources

**Files:**
- Modify: `core/src/test/java/io/github/jasper/monitoring/core/DefaultMonitoringRuntimeTest.java`
- Modify: `core/src/main/java/io/github/jasper/monitoring/core/application/DefaultMonitoringRuntime.java`

- [ ] **Step 1: Write a failing mixed-source runtime test**

Create an Action declaring `ResourceFact` from `METHOD_PARAMETER` and `DataCountFact` from `HOST_PROVIDER`. Construct one execution with both facts and a source map, then assert `FactCollection.getSources()` and persisted `EventFact` snapshots retain the two distinct values.

- [ ] **Step 2: Verify the focused test fails**

Run: `mvn -pl core -am -Dtest=DefaultMonitoringRuntimeTest test`

Expected: the mixed-source assertion fails because collection currently applies one source to the entire supplied set.

- [ ] **Step 3: Change collection to resolve a source per entry**

Replace the uniform supplied contribution call with an `addSupplied(...)` method that requires one source for each supplied Fact and delegates entry validation to the existing Action and FactDefinition checks. Keep `FactBinding` contributions uniform because each binding still declares one trusted source.

- [ ] **Step 4: Run API and core tests**

Run: `mvn -pl core -am -Dtest=ActionExecutionTest,DefaultMonitoringRuntimeTest test`

Expected: both test classes pass.

- [ ] **Step 5: Commit the core increment**

```text
feat(core): preserve per-fact source provenance
```

### Task 3: Runtime Fact Scope

**Files:**
- Create: `spring-support/src/test/java/io/github/jasper/monitoring/spring/support/MonitoringFactsTest.java`
- Create: `spring-support/src/main/java/io/github/jasper/monitoring/spring/support/MonitoringFacts.java`
- Create: `spring-support/src/main/java/io/github/jasper/monitoring/spring/support/MonitoringFactScope.java`

- [ ] **Step 1: Write failing scope tests**

Cover these observable behaviors with real scopes:

```java
try (MonitoringFactScope scope = MonitoringFactScope.open()) {
    assertTrue(MonitoringFacts.put(DataCountFact.class, 7L));
    assertEquals(7L, scope.snapshot().get(DataCountFact.class));
}
assertFalse(MonitoringFacts.put(DataCountFact.class, 8L));
```

Add separate tests for duplicate Fact rejection, nested scope isolation, two threads using latches, out-of-order close rejection, and cleanup after close. Capture `java.util.logging` output to assert the no-scope warning is observable and contains no Fact value.

- [ ] **Step 2: Verify the scope tests fail**

Run: `mvn -pl spring-support -am -Dtest=MonitoringFactsTest test`

Expected: compilation failure because the scope classes do not exist.

- [ ] **Step 3: Implement the minimal stack scope**

Implement `MonitoringFacts.put` against a private static `ThreadLocal<Deque<State>>`. `MonitoringFactScope.open()` pushes one state and returns a closeable handle. `snapshot()` returns an immutable `ActionFacts`; duplicate keys throw `IllegalStateException`; `close()` verifies stack identity and removes the ThreadLocal when empty.

- [ ] **Step 4: Run scope tests repeatedly**

Run: `mvn -pl spring-support -am -Dtest=MonitoringFactsTest test`

Expected: all scope, nesting, warning, concurrency, and cleanup tests pass.

- [ ] **Step 5: Commit the scope increment**

```text
feat(spring-support): add runtime action fact scope
```

### Task 4: Injected MonitoringRecorder

**Files:**
- Create: `spring-support/src/test/java/io/github/jasper/monitoring/spring/support/MonitoringRecorderTest.java`
- Create: `spring-support/src/main/java/io/github/jasper/monitoring/spring/support/MonitoringRecorder.java`
- Modify: `spring2-starter/src/main/java/io/github/jasper/monitoring/spring2/autoconfigure/AbnormalAccessMonitorAutoConfiguration.java`
- Modify: `spring3-starter/src/main/java/io/github/jasper/monitoring/spring3/autoconfigure/AbnormalAccessMonitorAutoConfiguration.java`
- Modify: `spring2-legacy-starter/src/main/java/io/github/jasper/monitoring/spring2/autoconfigure/AbnormalAccessMonitorAutoConfiguration.java`
- Modify: corresponding `TypedRuntimeAutoConfigurationTest.java` files.

- [ ] **Step 1: Write a failing recorder unit test**

Construct a real `MonitoringService` fixture and a fixed `MonitoringContextAccessor`. Call:

```java
recorder.record(QueryAction.class, ActionOutcome.success(3L), facts);
```

Assert the saved event uses the accessor request and identity and the collected Fact source is `HOST_PROVIDER`.

- [ ] **Step 2: Verify the recorder test fails**

Run: `mvn -pl spring-support -am -Dtest=MonitoringRecorderTest test`

Expected: compilation failure because `MonitoringRecorder` does not exist.

- [ ] **Step 3: Implement the recorder**

Store injected `MonitoringService` and `MonitoringContextAccessor`, reject null arguments, create `ActionExecution.of(actionType, request, identity, outcome, facts, FactSource.HOST_PROVIDER)`, and return the `AssemblyResult`.

- [ ] **Step 4: Add recorder beans and auto-configuration assertions**

Expose one conditional `MonitoringRecorder` bean beside the existing Servlet `MonitoringContextAccessor` in each starter. Assert it exists in Servlet contexts and remains absent in non-Web contexts.

- [ ] **Step 5: Run support and starter auto-configuration tests**

Run: `mvn -pl spring-support,spring2-starter,spring3-starter,spring2-legacy-starter -am -Dtest=MonitoringRecorderTest,TypedRuntimeAutoConfigurationTest test`

Expected: all selected tests pass in each relevant module.

- [ ] **Step 6: Commit the recorder increment**

```text
feat(spring-support): add concise monitoring recorder
```

### Task 5: Integrate Scope with All Annotation Aspects

**Files:**
- Modify: `spring2-starter/src/test/java/io/github/jasper/monitoring/spring2/autoconfigure/TypedMonitorActionAspectTest.java`
- Modify: `spring3-starter/src/test/java/io/github/jasper/monitoring/spring3/autoconfigure/TypedMonitorActionAspectTest.java`
- Modify: `spring2-legacy-starter/src/test/java/io/github/jasper/monitoring/spring2/autoconfigure/TypedMonitorActionAspectTest.java`
- Modify: corresponding production `TypedMonitorActionAspect.java` files.

- [ ] **Step 1: Write failing Spring 3 aspect tests**

Add monitored fixture methods that call `MonitoringFacts.put`. Assert runtime facts persist with `HOST_PROVIDER`, parameter facts remain `METHOD_PARAMETER`, duplicates fail, nested proxy calls isolate facts, and a throwing method records its runtime Fact before rethrowing.

- [ ] **Step 2: Verify Spring 3 tests fail**

Run: `mvn -pl spring3-starter -am -Dtest=TypedMonitorActionAspectTest test`

Expected: runtime Fact assertions fail because the aspect does not open or consume a scope.

- [ ] **Step 3: Implement Spring 3 aspect scope lifecycle**

Open a `MonitoringFactScope` before `invocation.proceed()`. On success or failure, snapshot runtime facts, reject overlap with parameter facts, create the exact per-Fact source map, and submit. Close in `finally`, preserving the existing suppressed-monitoring-exception behavior.

- [ ] **Step 4: Run Spring 3 tests**

Run: `mvn -pl spring3-starter -am -Dtest=TypedMonitorActionAspectTest test`

Expected: all aspect tests pass.

- [ ] **Step 5: Add equivalent failing tests and implementation to Spring 2**

Run red then green: `mvn -pl spring2-starter -am -Dtest=TypedMonitorActionAspectTest test`

Expected final result: all Spring 2 aspect tests pass with behavior matching Spring 3.

- [ ] **Step 6: Add equivalent failing tests and implementation to Spring 2 Legacy**

Run red then green: `mvn -pl spring2-legacy-starter -am -Dtest=TypedMonitorActionAspectTest test`

Expected final result: all legacy aspect tests pass with behavior matching the other starters.

- [ ] **Step 7: Commit the aspect increment**

```text
feat(starters): collect facts added inside monitored methods
```

### Task 6: Integration Audit Through an Ordinary Service

**Files:**
- Create: `integration-audit/spring3-web/src/main/java/io/github/jasper/monitoring/audit/spring3/monitoring/AnnotatedMonitoringService.java`
- Create: `integration-audit/spring2-web/src/main/java/io/github/jasper/monitoring/audit/spring2/monitoring/AnnotatedMonitoringService.java`
- Modify: Spring 2/3 `monitoring/MonitoringFixtureController.java`
- Modify: Spring 2/3 `Spring2AuditWebAcceptanceTest.java` and `Spring3AuditWebAcceptanceTest.java`

- [ ] **Step 1: Update IA-04 tests first**

Change IA-04 to expect the server-computed value from the ordinary Service and `source_type='HOST_PROVIDER'`, regardless of the client row count.

- [ ] **Step 2: Verify both IA-04 tests fail**

Run: `mvn -pl integration-audit/spring2-web,integration-audit/spring3-web -am -Dtest=Spring2AuditWebAcceptanceTest#ia04_nestedActionFactsAreTypedAndPrevalidated,Spring3AuditWebAcceptanceTest#ia04_nestedActionFactsAreTypedAndPrevalidated test`

Expected: assertions fail because the existing Controller still records client parameter facts as `METHOD_PARAMETER`.

- [ ] **Step 3: Add ordinary annotated Services and delegate from Controllers**

Each Service exposes a public `@MonitorAction(BuiltInActions.SensitiveView.class)` method, computes a fixed server-side count from the fixture request, calls `MonitoringFacts.put(BuiltInFacts.DataCount.class, count)`, and returns the existing response. Controllers remain HTTP adapters and delegate through the injected Spring proxy.

- [ ] **Step 4: Run IA-04 and acceptance ID verification**

Run: `mvn -pl integration-audit/spring2-web,integration-audit/spring3-web -am -Dtest=Spring2AuditWebAcceptanceTest#ia04_nestedActionFactsAreTypedAndPrevalidated,Spring3AuditWebAcceptanceTest#ia04_nestedActionFactsAreTypedAndPrevalidated test`

Run: `mvn -pl integration-audit verify`

Expected: both IA-04 tests and Boot parity verification pass.

- [ ] **Step 5: Commit the integration audit increment**

```text
test(integration-audit): cover runtime facts in service methods
```

### Task 7: Standalone Annotation Monitoring Documentation

**Files:**
- Create: `docs/注解监听与运行时事实作用域.md`
- Modify: `README.md`
- Modify: `docs/集成指南.md`
- Modify: `integration-audit/spring2-web/README.md`
- Modify: `integration-audit/spring3-web/README.md`

- [ ] **Step 1: Write the standalone guide**

Document the architecture flow, interception sequence, Controller and Service examples, selection between `@ActionFact`, `MonitoringFacts.put`, `MonitoringRecorder.record`, and full `MonitoringService.monitor`, plus proxy, self-invocation, nesting, concurrency, exception, and async boundaries.

- [ ] **Step 2: Link the guide from existing entry points**

Add concise links without duplicating the guide. Preserve all current user edits in these already-modified files.

- [ ] **Step 3: Verify documentation references and formatting**

Run: `rg -n "注解监听与运行时事实作用域|MonitoringFacts|MonitoringRecorder" README.md docs integration-audit/*/README.md`

Run: `git diff --check`

Expected: every entry point links the guide and no whitespace errors are reported.

- [ ] **Step 4: Commit the documentation increment**

```text
docs: explain annotation monitoring fact scope
```

### Task 8: Full Verification

**Files:**
- No production edits unless a failing test exposes a defect; any defect receives a focused failing regression test first.

- [ ] **Step 1: Run focused affected-module tests**

Run: `mvn -pl api,core,spring-support,spring2-starter,spring3-starter,spring2-legacy-starter,integration-audit/spring2-web,integration-audit/spring3-web -am test`

Expected: all affected module tests pass with zero failures and errors.

- [ ] **Step 2: Run the full reactor**

Run: `mvn clean verify -DskipTests=false`

Expected: reactor build succeeds and all tests pass.

- [ ] **Step 3: Inspect final scope and provenance diff**

Run: `git diff --check`

Run: `git status --short`

Confirm only intended feature changes plus the user's pre-existing unrelated modifications remain, no generated artifacts are staged, and no credentials or raw sensitive payloads were introduced.

# Static Action Fact Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a repeatable method annotation that supplies validated, typed constant Facts to `@MonitorAction` invocations.

**Architecture:** `api` owns the framework-neutral annotation. `MonitorActionContractValidator` decodes and validates literals once while compiling a method binding, and each starter merges the immutable static facts with parameter and runtime facts before the existing strict validation path.

**Tech Stack:** Java 8 annotations and reflection, Maven reactor, JUnit 5, Spring AOP, Spring Boot 2/3.

---

### Task 1: Public Static Fact Annotation

**Files:**
- Create: `api/src/main/java/io/github/jasper/monitoring/api/fact/StaticActionFact.java`
- Modify: `api/src/test/java/io/github/jasper/monitoring/api/action/TypedActionContractTest.java`

- [ ] **Step 1: Write the failing reflection test**

Declare two `@StaticActionFact` annotations on a monitored fixture method and assert
`method.getAnnotationsByType(StaticActionFact.class)` returns both fact tokens and literal values.

- [ ] **Step 2: Run the API test and verify red**

Run: `mvn -pl api "-Dtest=TypedActionContractTest" test`

Expected: test compilation fails because `StaticActionFact` does not exist.

- [ ] **Step 3: Add the minimal repeatable annotation**

Create a runtime-retained, documented, method-targeted annotation:

```java
@Repeatable(StaticActionFact.List.class)
public @interface StaticActionFact {
    Class<? extends FactType<?>> fact();
    String value();

    @Documented
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @interface List {
        StaticActionFact[] value();
    }
}
```

- [ ] **Step 4: Run the API test and verify green**

Run: `mvn -pl api "-Dtest=TypedActionContractTest" test`

Expected: all `TypedActionContractTest` tests pass.

### Task 2: Compile Static Facts Into Method Bindings

**Files:**
- Modify: `spring-support/src/main/java/io/github/jasper/monitoring/spring/support/MonitorActionContractValidator.java`
- Modify: `spring-support/src/test/java/io/github/jasper/monitoring/spring/support/MonitorActionContractValidatorTest.java`

- [ ] **Step 1: Write failing contract tests**

Add fixtures and assertions proving that a static ResourceId literal is trimmed/decoded into
`MethodBinding.getStaticFacts()`, while an invalid DataCount literal, undeclared Fact, duplicate static Fact,
static/parameter duplicate, and static/provider duplicate raise `MonitoringConfigurationException`.

- [ ] **Step 2: Run the Spring Support test and verify red**

Run: `mvn -pl spring-support -am "-Dtest=MonitorActionContractValidatorTest" "-Dsurefire.failIfNoSpecifiedTests=false" test`

Expected: test compilation fails because `MethodBinding.getStaticFacts()` is absent.

- [ ] **Step 3: Compile and validate static annotations**

In `compile(Method)`, iterate `method.getAnnotationsByType(StaticActionFact.class)`, require the Fact from the
catalog, verify Action declaration and `HOST_PROVIDER` on both Action and FactDefinition, reject ownership/provider
conflicts, decode with `definition.decode(annotation.value())`, and build immutable `ActionFacts`. Wrap decoding and
validation failures in `MonitoringConfigurationException`.

Extend `MethodBinding` with:

```java
private final ActionFacts staticFacts;
public ActionFacts getStaticFacts() { return staticFacts; }
```

- [ ] **Step 4: Run the Spring Support test and verify green**

Run the command from Step 2 and expect all validator tests to pass.

### Task 3: Merge Static Facts In Every Starter

**Files:**
- Modify: `spring2-starter/src/main/java/io/github/jasper/monitoring/spring2/autoconfigure/TypedMonitorActionAspect.java`
- Modify: `spring2-starter/src/test/java/io/github/jasper/monitoring/spring2/autoconfigure/TypedMonitorActionAspectTest.java`
- Modify: `spring3-starter/src/main/java/io/github/jasper/monitoring/spring3/autoconfigure/TypedMonitorActionAspect.java`
- Modify: `spring3-starter/src/test/java/io/github/jasper/monitoring/spring3/autoconfigure/TypedMonitorActionAspectTest.java`
- Modify: `spring2-legacy-starter/src/main/java/io/github/jasper/monitoring/spring2/autoconfigure/TypedMonitorActionAspect.java`
- Modify: `spring2-legacy-starter/src/test/java/io/github/jasper/monitoring/spring2/autoconfigure/TypedMonitorActionAspectTest.java`

- [ ] **Step 1: Write failing starter tests**

For Spring 2 and Spring 3, declare a ReportExport method with static ResourceId, put DataCount at runtime, call
`MonitoringGate.checkpoint()`, and assert persisted ResourceId has `HOST_PROVIDER` source. For legacy, declare both
required facts statically without checkpoint and assert the success event contains their normalized values.

- [ ] **Step 2: Run focused starter tests and verify red**

Run:

```text
mvn -pl spring2-legacy-starter,spring2-starter,spring3-starter -am "-Dtest=TypedMonitorActionAspectTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Expected: calls fail required-Fact validation because static facts are not merged.

- [ ] **Step 3: Merge in deterministic source order**

Before parameter and runtime facts, add `binding.getStaticFacts()` using `FactSource.HOST_PROVIDER`. Preserve the
existing duplicate rejection in `addFacts(...)`, then pass the merged facts through `ActionFactExtractor.validate`.
Apply identical behavior to Spring 2 and Spring 3 checkpoint/monitor paths and the legacy monitor path.

- [ ] **Step 4: Run focused starter tests and verify green**

Run the command from Step 2 and expect all three `TypedMonitorActionAspectTest` suites to pass.

### Task 4: Regression Verification

**Files:**
- Verify all files above plus the existing checkpoint validation changes in the working tree.

- [ ] **Step 1: Run diff hygiene checks**

Run: `git diff --check`

Expected: exit code 0 with no whitespace errors.

- [ ] **Step 2: Run affected module tests**

Run: `mvn -pl spring-support,spring2-legacy-starter,spring2-starter,spring3-starter -am test`

Expected: reactor success with zero test failures and errors.

- [ ] **Step 3: Run repository verification**

Run: `mvn clean verify "-DskipTests=false"`

Expected: all 13 reactor modules succeed, including Spring 2 and Spring 3 integration audit modules.

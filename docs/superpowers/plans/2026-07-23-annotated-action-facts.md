# 注解动作事实采集 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让 MVC `@MonitorAction` 可安全采集参数和执行结果中的动态事实，修复匿名登录失败聚合，并在接入指南中明确动作支持、宿主接入义务和人工审核边界。

**Architecture:** `api` 定义静态属性与受限的动态事实契约；`core` 统一把静态属性写入草稿并以账户哈希修正认证规则；Spring 2/3 Starter 在现有 Servlet 拦截器与新增 AOP 组件之间共享请求私有事实。拦截器仍拥有可信上下文和最终 HTTP 语义，采集器只能补充动态业务事实。

**Tech Stack:** Java 8 API/Core, JUnit 5, Spring Boot 2.7 / Spring Boot 3.2, Spring AOP / AspectJ Weaver, TestRestTemplate, Maven.

---

## File Map

| 文件 | 责任 |
| --- | --- |
| `api/.../MonitorAction.java` | 声明采集器类型，保留动作静态定义。 |
| `api/.../MonitorActionAttribute.java`、`MonitorActionAttributes.java`、`MonitorActionAttributeTarget.java` | 可重复的静态属性与参数事实绑定注解。 |
| `api/.../MonitorActionDefinition.java` | 解析注解元素和注册式定义的静态属性。 |
| `api/.../MonitorActionInvocation.java`、`MonitorActionFacts.java`、`MonitorActionEnricher.java` | 框架无关的调用快照、受限事实和宿主扩展接口。 |
| `core/.../ActionEventRecorder.java` | 将静态属性放入预填充草稿。 |
| `core/.../DefaultRuleCatalog.java` | 用 `attempted_account_hash` 归并 `AUTH-01`/`AUTH-02` 的匿名登录失败。 |
| `spring-support/.../AnnotatedActionFacts.java`、`BoundParameterFactsExtractor.java` | 聚合调用阶段的动态事实，并用受限 Bean 路径提取已绑定参数。 |
| `spring2-starter/...`、`spring3-starter/...` | 请求状态、AOP 采集器、拦截器、自动配置和依赖的 Boot 2/3 对等实现。 |
| `integration-audit/spring2-web/...`、`integration-audit/spring3-web/...` | 真实 HTTP 验收控制器、事实采集器和回归断言。 |
| `docs/集成指南.md` | 动作编码支持矩阵、接入级别、控制触发与审批规则。 |

### Task 1: Define Static Metadata and Dynamic Fact Contracts

**Files:**
- Create: `api/src/main/java/io/github/jasper/monitoring/api/MonitorActionAttribute.java`
- Create: `api/src/main/java/io/github/jasper/monitoring/api/MonitorActionAttributes.java`
- Create: `api/src/main/java/io/github/jasper/monitoring/api/MonitorActionAttributeTarget.java`
- Create: `api/src/main/java/io/github/jasper/monitoring/api/MonitorActionInvocation.java`
- Create: `api/src/main/java/io/github/jasper/monitoring/api/MonitorActionFacts.java`
- Create: `api/src/main/java/io/github/jasper/monitoring/api/MonitorActionEnricher.java`
- Modify: `api/src/main/java/io/github/jasper/monitoring/api/MonitorAction.java`
- Modify: `api/src/main/java/io/github/jasper/monitoring/api/MonitorActionDefinition.java`
- Create: `api/src/test/java/io/github/jasper/monitoring/api/MonitorActionDefinitionTest.java`
- Create: `api/src/test/java/io/github/jasper/monitoring/api/MonitorActionFactsTest.java`

- [ ] **Step 1: Write failing API tests for annotations and fact boundaries.**

```java
@Test
void resolvesStaticAttributesFromTheSameAnnotatedElement() throws Exception {
    MonitorActionDefinition definition = MonitorActionDefinition.from(
        DeclaredActions.class.getMethod("export", String.class));

    assertEquals("report:export", definition.getAction());
    assertEquals("HIGH", definition.getAttributes().get("sensitivity"));
    assertEquals("true", definition.getAttributes().get("high_privilege"));
}

@Test
void rejectsStaticAttributesThatUseReservedRuleTagKeys() throws Exception {
    assertThrows(IllegalArgumentException.class, () -> MonitorActionDefinition.from(
        DeclaredActions.class.getMethod("invalid")));
}

private static final class DeclaredActions {
    @MonitorAction(value = "report:export", eventType = SecurityEventType.EXPORT)
    @MonitorActionAttribute(name = "sensitivity", value = "HIGH")
    @MonitorActionAttribute(name = "high_privilege", value = "true")
    public void export(String reportId) { }

    @MonitorAction("report:invalid")
    @MonitorActionAttribute(name = "monitor.rule-tag.manual", value = "true")
    public void invalid() { }

    public void exportRequest(
        @MonitorActionAttribute(target = MonitorActionAttributeTarget.RESOURCE_ID, path = "report.id")
        ExportRequest request) { }
}
```

```java
@Test
void keepsOnlyAllowedSanitizedDynamicFacts() throws Exception {
    MonitorActionInvocation invocation = MonitorActionInvocation.before(
        MonitorActionDefinition.builder("report:export").build(),
        Sample.class.getMethod("export", String.class), new Object[] { "report-1" });

    MonitorActionFacts facts = MonitorActionFacts.builder()
        .resourceId("report-1\n")
        .dataCount(5)
        .attribute("sensitivity", "HIGH")
        .build();

    assertEquals("report-1", facts.getResourceId());
    assertEquals(5L, facts.getDataCount().getAsLong());
    assertEquals("HIGH", facts.getAttributes().get("sensitivity"));
    assertEquals(MonitorActionInvocation.Phase.BEFORE, invocation.getPhase());
}

@Test
void rejectsForbiddenDynamicFactAttributes() {
    assertThrows(IllegalArgumentException.class, () -> MonitorActionFacts.builder()
        .attribute("password", "secret").build());
}

@Test
void declaresAParameterBeanPathForAnAllowedDynamicTarget() throws Exception {
    MonitorActionAttribute attribute = DeclaredActions.class
        .getMethod("exportRequest", ExportRequest.class)
        .getParameters()[0].getAnnotation(MonitorActionAttribute.class);

    assertEquals(MonitorActionAttributeTarget.RESOURCE_ID, attribute.target());
    assertEquals("report.id", attribute.path());
}

private static final class ExportRequest {
    private final Report report = new Report();
    public Report getReport() { return report; }
}

private static final class Report {
    public String getId() { return "report-1"; }
}
```

- [ ] **Step 2: Run the tests and confirm the missing public API causes the expected failure.**

Run: `mvn -pl api -Dtest=MonitorActionDefinitionTest,MonitorActionFactsTest test`

Expected: compilation failure because `MonitorActionAttribute`, `MonitorActionFacts`, and the element-based definition resolver do not exist.

- [ ] **Step 3: Implement the API contracts with no Spring dependency.**

```java
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD, ElementType.PARAMETER})
@Repeatable(MonitorActionAttributes.class)
public @interface MonitorActionAttribute {
    MonitorActionAttributeTarget target() default MonitorActionAttributeTarget.ATTRIBUTE;
    String name() default "";
    String value() default "";
    String path() default "";
}

@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD, ElementType.PARAMETER})
public @interface MonitorActionAttributes {
    MonitorActionAttribute[] value();
}

public interface MonitorActionEnricher {
    MonitorActionFacts enrich(MonitorActionInvocation invocation);
}
```

Add `Class<? extends MonitorActionEnricher>[] enrichers() default {};` to `MonitorAction`. Define `MonitorActionAttributeTarget` as `ATTRIBUTE`, `RESOURCE_ID`, and `ORG_SCOPE`. On types or methods, validate that the target is `ATTRIBUTE`, `name` and `value` are non-empty, and `path` is empty; on parameters, validate that `value` is empty and `ATTRIBUTE` has a non-empty `name`. Implement `MonitorActionInvocation.before(...)`, `returning(...)`, and `throwing(...)` with `BEFORE`, `AFTER_RETURNING`, and `AFTER_THROWING` phases; defensively copy arguments, expose the `Method`, definition, return value, failure and non-negative elapsed milliseconds. Implement `MonitorActionFacts.empty()` and a Builder with nullable optional dynamic fields plus an immutable, sanitized attribute map; use `OptionalLong` for optional counts and `SecurityFieldSanitizer.requireSafeAttributeKey` for every attribute key.

Extend `MonitorActionDefinition` as follows:

```java
public static MonitorActionDefinition from(AnnotatedElement element) {
    MonitorAction action = element.getAnnotation(MonitorAction.class);
    if (action == null) {
        throw new IllegalArgumentException("MonitorAction is required");
    }
    Builder builder = from(action).toBuilder();
    for (MonitorActionAttribute attribute : element.getAnnotationsByType(MonitorActionAttribute.class)) {
        if (attribute.target() != MonitorActionAttributeTarget.ATTRIBUTE
            || !attribute.path().isEmpty()) {
            throw new IllegalArgumentException("Type and method MonitorActionAttribute declarations are static attributes only");
        }
        builder.attribute(attribute.name(), attribute.value());
    }
    return builder.build();
}

public Builder attribute(String key, String value) {
    String safeKey = SecurityFieldSanitizer.text(key, 128);
    SecurityFieldSanitizer.requireSafeAttributeKey(safeKey);
    if (safeKey.startsWith(RULE_TAG_PREFIX)) {
        throw new IllegalArgumentException("MonitorAction static attributes cannot use " + RULE_TAG_PREFIX);
    }
    attributes.put(safeKey, SecurityFieldSanitizer.text(value, 512));
    return this;
}
```

Add immutable `Map<String, String> attributes`, a `getAttributes()` accessor, `toBuilder()`, and equality/hash-code coverage. Preserve `from(MonitorAction)` for binary source compatibility; it resolves only the annotation fields because an annotation proxy cannot reveal its declaring element.

- [ ] **Step 4: Run the focused API tests and then the whole API module.**

Run: `mvn -pl api -Dtest=MonitorActionDefinitionTest,MonitorActionFactsTest test`

Expected: PASS.

Run: `mvn -pl api test`

Expected: PASS with all existing API contract tests.

- [ ] **Step 5: Commit the API contract.**

```bash
git add api/src/main/java/io/github/jasper/monitoring/api/MonitorAction.java api/src/main/java/io/github/jasper/monitoring/api/MonitorActionAttribute.java api/src/main/java/io/github/jasper/monitoring/api/MonitorActionAttributes.java api/src/main/java/io/github/jasper/monitoring/api/MonitorActionDefinition.java api/src/main/java/io/github/jasper/monitoring/api/MonitorActionInvocation.java api/src/main/java/io/github/jasper/monitoring/api/MonitorActionFacts.java api/src/main/java/io/github/jasper/monitoring/api/MonitorActionEnricher.java api/src/test/java/io/github/jasper/monitoring/api/MonitorActionDefinitionTest.java api/src/test/java/io/github/jasper/monitoring/api/MonitorActionFactsTest.java
git commit -m "feat(api): define annotated action facts"
```

### Task 2: Preserve Static Attributes and Fix Anonymous Login Aggregation

**Files:**
- Modify: `core/src/main/java/io/github/jasper/monitoring/core/application/ActionEventRecorder.java`
- Modify: `core/src/main/java/io/github/jasper/monitoring/core/domain/rule/DefaultRuleCatalog.java`
- Modify: `core/src/test/java/io/github/jasper/monitoring/core/ActionEventRecorderTest.java`
- Create: `core/src/test/java/io/github/jasper/monitoring/core/DefaultRuleCatalogAuthenticationTest.java`

- [ ] **Step 1: Add failing Core tests for static attributes and attempted-account aggregation.**

```java
@Test
void addsRegisteredStaticAttributesBeforeDynamicFacts() {
    MonitoringActionRegistry actions = new MonitoringActionRegistry().register(
        MonitorActionDefinition.builder("report:export")
            .eventType(SecurityEventType.EXPORT)
            .attribute("sensitivity", "HIGH")
            .build());
    ActionEventRecorder recorder = new ActionEventRecorder(monitor, Clock.systemUTC(), actions);

    recorder.record(recorder.draft("report:export", request(), identity())
        .result(SecurityEventResult.SUCCESS).build());

    assertEquals("HIGH", monitor.draft.getAttribute("sensitivity"));
}
```

```java
@Test
void matchesOneIpAcrossTenAnonymousAttemptedAccounts() {
    DetectionRule rule = findRule("AUTH-02");
    List<SecurityEvent> events = failuresForOneIpAndTenAccountHashes();

    assertTrue(rule.evaluate(events.get(9), events).isPresent());
}

@Test
void doesNotMergeDifferentAnonymousAccountsIntoOneAuthOneWindow() {
    DetectionRule rule = findRule("AUTH-01");
    List<SecurityEvent> events = failuresForDifferentAccountHashes();

    assertFalse(rule.evaluate(events.get(4), events).isPresent());
}
```

- [ ] **Step 2: Run the focused tests and confirm current behavior fails.**

Run: `mvn -pl core -am -Dtest=ActionEventRecorderTest,DefaultRuleCatalogAuthenticationTest test`

Expected: `attribute(String,String)` is absent and the anonymous `AUTH-02` scenario does not match because `SecurityEvent.subject()` resolves to the shared source IP.

- [ ] **Step 3: Implement static attribute propagation and login-subject helpers.**

In `ActionEventRecorder.draft(MonitorActionDefinition, ...)`, add static attributes before rule tags:

```java
for (Map.Entry<String, String> attribute : action.getAttributes().entrySet()) {
    draft.attribute(attribute.getKey(), attribute.getValue());
}
for (String ruleTag : action.getRuleTags()) {
    draft.attribute(MonitorActionDefinition.ruleTagAttributeKey(ruleTag), "true");
}
```

Replace the `AUTH-01` `WindowAggregateRule` with an `AbstractDetectionRule` that counts only `LOGIN_FAILURE` events in the five-minute window with the same `loginSubject(event)`. Rework `authTwo()` to place `loginSubject(candidate)` into the distinct-account set. Use these helpers:

```java
private static String loginSubject(SecurityEvent event) {
    String attempted = event.getAttribute("attempted_account_hash");
    return attempted == null || attempted.isEmpty() ? event.subject() : "attempted:" + attempted;
}

private static boolean sameLoginSubject(SecurityEvent left, SecurityEvent right) {
    return loginSubject(left).equals(loginSubject(right));
}
```

For `AUTH-01`, call `match(event, loginSubject(event), Duration.ofMinutes(15))` so alert identity and controls remain per attempted account. Preserve the prior authenticated-user or source-IP fallback when the hash is absent.

- [ ] **Step 4: Run focused Core tests and all Core tests.**

Run: `mvn -pl core -am -Dtest=ActionEventRecorderTest,DefaultRuleCatalogAuthenticationTest test`

Expected: PASS.

Run: `mvn -pl core -am test`

Expected: PASS with all 14 baseline rules still present.

- [ ] **Step 5: Commit the Core behavior.**

```bash
git add core/src/main/java/io/github/jasper/monitoring/core/application/ActionEventRecorder.java core/src/main/java/io/github/jasper/monitoring/core/domain/rule/DefaultRuleCatalog.java core/src/test/java/io/github/jasper/monitoring/core/ActionEventRecorderTest.java core/src/test/java/io/github/jasper/monitoring/core/DefaultRuleCatalogAuthenticationTest.java
git commit -m "fix(core): group anonymous login failures by account hash"
```

### Task 3: Add Request-Scoped MVC Fact Capture in Both Starters

**Files:**
- Create: `spring-support/src/main/java/io/github/jasper/monitoring/spring/support/AnnotatedActionFacts.java`
- Create: `spring-support/src/main/java/io/github/jasper/monitoring/spring/support/BoundParameterFactsExtractor.java`
- Modify: `spring2-starter/pom.xml`
- Modify: `spring3-starter/pom.xml`
- Modify: `spring2-starter/src/main/java/io/github/jasper/monitoring/spring2/autoconfigure/AnnotatedMonitoringInterceptor.java`
- Create: `spring2-starter/src/main/java/io/github/jasper/monitoring/spring2/autoconfigure/AnnotatedActionFactsAspect.java`
- Modify: `spring2-starter/src/main/java/io/github/jasper/monitoring/spring2/autoconfigure/AbnormalAccessMonitorAutoConfiguration.java`
- Modify: `spring3-starter/src/main/java/io/github/jasper/monitoring/spring3/autoconfigure/AnnotatedActionMonitoringInterceptor.java`
- Create: `spring3-starter/src/main/java/io/github/jasper/monitoring/spring3/autoconfigure/AnnotatedActionFactsAspect.java`
- Modify: `spring3-starter/src/main/java/io/github/jasper/monitoring/spring3/autoconfigure/AbnormalAccessMonitorAutoConfiguration.java`
- Modify: `spring2-starter/src/test/java/io/github/jasper/monitoring/spring2/autoconfigure/AbnormalAccessMonitorAutoConfigurationTest.java`
- Modify: `spring3-starter/src/test/java/io/github/jasper/monitoring/spring3/autoconfigure/AbnormalAccessMonitorAutoConfigurationTest.java`

- [ ] **Step 1: Write failing Boot 2 and Boot 3 tests for AOP registration and HTTP-result precedence.**

Add the same test shape to both Starter tests, using a proxied non-final controller bean and an `ActionEventRecorder` backed by the existing capturing monitor:

```java
@Test
void registersAnnotatedActionFactsAspectWhenInstrumentationIsEnabled() {
    webContextRunner.run(context ->
        assertThat(context).hasSingleBean(AnnotatedActionFactsAspect.class));
}

@Test
void recordsFactsFromParametersAndReturnValueButLetsHttpDeniedWin() throws Exception {
    // Bind a MockHttpServletRequest, call the Spring proxy method, then invoke afterCompletion with 403.
    // The configured enricher returns resourceId, dataCount, SUCCESS and EXPORT_COMPLETED.
    assertThat(monitor.events.get(0).getResourceId()).isEqualTo("report-9");
    assertThat(monitor.events.get(0).getDataCount()).isEqualTo(5000L);
    assertThat(monitor.events.get(0).getResult()).isEqualTo(SecurityEventResult.DENIED);
    assertThat(monitor.events.get(0).getReasonCode()).isEqualTo("HTTP_403");
}

@Test
void extractsNestedBeanPropertiesFromAnnotatedParameters() throws Exception {
    // Controller parameter: @MonitorActionAttribute(target = RESOURCE_ID, path = "report.id") ExportRequest request.
    // Invoke the proxy with ExportRequest(reportId = "report-9", tenant = "org-a").
    assertThat(monitor.events.get(0).getResourceId()).isEqualTo("report-9");
    assertThat(monitor.events.get(0).getOrgScope()).isEqualTo("org-a");
}

@Test
void ignoresInvalidOrNullParameterPathsWithoutChangingTheControllerResult() throws Exception {
    // A path of "report.class.name" or a null nested value produces no mapped fact and still returns normally.
    assertThat(controllerResponse).isEqualTo("ok");
    assertThat(monitor.events.get(0).getResourceId()).isNull();
}
```

Add a failing-enricher assertion: a `RuntimeException` from one `MonitorActionEnricher` leaves the controller response and the static action event intact, with no dynamic fields from that enricher.

- [ ] **Step 2: Run each focused Starter test and confirm the aspect is absent.**

Run: `mvn -pl spring2-starter -am -Dtest=AbnormalAccessMonitorAutoConfigurationTest test`

Expected: compilation failure because `AnnotatedActionFactsAspect` and request fact storage do not exist.

Run: `mvn -pl spring3-starter -am -Dtest=AbnormalAccessMonitorAutoConfigurationTest test`

Expected: the same failure under the `jakarta.servlet` module.

- [ ] **Step 3: Implement the shared accumulator and exact Boot 2/3 adapters.**

Create `AnnotatedActionFacts` in `spring-support` with an immutable `MonitorActionDefinition`, source `Method`, ordered enricher types, and synchronized merge/apply operations:

```java
public void merge(MonitorActionFacts value) {
    if (value == null) { return; }
    resourceId = value.getResourceId() == null ? resourceId : value.getResourceId();
    orgScope = value.getOrgScope() == null ? orgScope : value.getOrgScope();
    dataCount = value.getDataCount().isPresent() ? value.getDataCount() : dataCount;
    latencyMs = value.getLatencyMs().isPresent() ? value.getLatencyMs() : latencyMs;
    result = value.getResult() == null ? result : value.getResult();
    reasonCode = value.getReasonCode() == null ? reasonCode : value.getReasonCode();
    for (Map.Entry<String, String> entry : value.getAttributes().entrySet()) {
        if (!definition.getAttributes().containsKey(entry.getKey())) { attributes.put(entry.getKey(), entry.getValue()); }
    }
}
```

Create `BoundParameterFactsExtractor` in `spring-support`. It examines only `Method.getParameters()` bearing `@MonitorActionAttribute`, uses the parameter index as the root object, and returns one `MonitorActionFacts` value per mapping. Its parser must accept only `identifier ('.' identifier | '[' non-negative-integer ']')*`; it resolves identifiers through public Java Bean getters or public fields and indexes only Java arrays and `List` values. It must reject `class` in every segment and reject all method calls, maps, static fields, negative indexes and malformed expressions. A missing property, null intermediate value, out-of-range index or getter exception returns no fact for that annotation. Apply parameter facts before custom enrichers; later enrichers may replace mapped dynamic fields, but neither may overwrite `definition.getAttributes()`.

```java
private MonitorActionFacts extract(MonitorActionAttribute attribute, Object root) {
    Object value = resolvePath(root, attribute.path());
    if (value == null) { return MonitorActionFacts.empty(); }
    String text = String.valueOf(value);
    if (attribute.target() == MonitorActionAttributeTarget.RESOURCE_ID) {
        return MonitorActionFacts.builder().resourceId(text).build();
    }
    if (attribute.target() == MonitorActionAttributeTarget.ORG_SCOPE) {
        return MonitorActionFacts.builder().orgScope(text).build();
    }
    return MonitorActionFacts.builder().attribute(attribute.name(), text).build();
}
```

In each interceptor, replace the stored `MonitorAction` with `AnnotatedActionFacts` built from the selected method or bean type element through `MonitorActionDefinition.from(element)`. At completion, call `recorder.draft(state.getDefinition(), request, identity)`, apply accumulated dynamic facts, then apply final result/reason according to this order: exception, 401/403, other 4xx/5xx, accumulated business result/reason, default success.

Add non-optional `org.springframework:spring-aop` and `org.aspectj:aspectjweaver` dependencies to both Starter POMs. Under the existing servlet/MVC/property conditions, register `@EnableAspectJAutoProxy` and one `@Aspect` bean. The aspect must:

```java
@Around("execution(public * *(..)) && (@annotation(io.github.jasper.monitoring.api.MonitorAction)"
    + " || @within(io.github.jasper.monitoring.api.MonitorAction))")
public Object collect(ProceedingJoinPoint point) throws Throwable {
    AnnotatedActionFacts state = stateForCurrentRequest(point);
    if (state == null || !state.matches(AopUtils.getMostSpecificMethod(
            ((MethodSignature) point.getSignature()).getMethod(), point.getTarget().getClass()))) {
        return point.proceed();
    }
    state.mergeAll(parameterFacts.extract(state.getMethod(), point.getArgs()));
    collect(state, MonitorActionInvocation.before(state.getDefinition(), state.getMethod(), point.getArgs()));
    long started = System.nanoTime();
    try {
        Object returned = point.proceed();
        collect(state, MonitorActionInvocation.returning(state.getDefinition(), state.getMethod(), point.getArgs(),
            returned, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)));
        return returned;
    } catch (Throwable failure) {
        collect(state, MonitorActionInvocation.throwing(state.getDefinition(), state.getMethod(), point.getArgs(),
            failure, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)));
        throw failure;
    }
}
```

Resolve every declared enricher using `ListableBeanFactory.getBean(type)`. Catch `RuntimeException` around resolution and invocation, skip only that enricher, and never alter `proceed()` or the completed response. The Boot 2 adapter imports `javax.servlet` and the Boot 3 adapter imports `jakarta.servlet`; all other semantics and tests stay identical. Document in Javadoc that dynamic annotation capture requires a non-final proxyable MVC controller; asynchronous and streaming handlers continue to use `ActionEventRecorder.draft(...)`.

- [ ] **Step 4: Run focused and complete Starter test suites.**

Run: `mvn -pl spring2-starter -am -Dtest=AbnormalAccessMonitorAutoConfigurationTest test`

Expected: PASS.

Run: `mvn -pl spring3-starter -am -Dtest=AbnormalAccessMonitorAutoConfigurationTest test`

Expected: PASS.

Run: `mvn -pl spring2-starter,spring3-starter -am test`

Expected: PASS; Boot 2 retains `javax.servlet`, Boot 3 retains `jakarta.servlet`.

- [ ] **Step 5: Commit the Starter adapters.**

```bash
git add spring-support/src/main/java/io/github/jasper/monitoring/spring/support/AnnotatedActionFacts.java spring2-starter spring3-starter
git commit -m "feat(starter): enrich annotated MVC action events"
```

### Task 4: Exercise Dynamic Annotation Facts Through Both Real Web Applications

**Files:**
- Create: `integration-audit/spring2-web/src/main/java/io/github/jasper/monitoring/audit/spring2/AuditExportFacts.java`
- Create: `integration-audit/spring2-web/src/main/java/io/github/jasper/monitoring/audit/spring2/AuditExportRequest.java`
- Modify: `integration-audit/spring2-web/src/main/java/io/github/jasper/monitoring/audit/spring2/AuditController.java`
- Modify: `integration-audit/spring2-web/src/test/java/io/github/jasper/monitoring/audit/spring2/Spring2AuditWebAcceptanceTest.java`
- Create: `integration-audit/spring3-web/src/main/java/io/github/jasper/monitoring/audit/spring3/AuditExportFacts.java`
- Create: `integration-audit/spring3-web/src/main/java/io/github/jasper/monitoring/audit/spring3/AuditExportRequest.java`
- Modify: `integration-audit/spring3-web/src/main/java/io/github/jasper/monitoring/audit/spring3/AuditController.java`
- Modify: `integration-audit/spring3-web/src/test/java/io/github/jasper/monitoring/audit/spring3/Spring3AuditWebAcceptanceTest.java`

- [ ] **Step 1: Add failing HTTP acceptance tests.**

Add this equivalent test to each web project:

```java
@Test
void recordsAnnotatedExportFactsFromNestedRequestBindingAndReturnValue() {
    ResponseEntity<String> response = restTemplate.postForEntity(url("/audit/annotated-export"),
        request("audit-export-2026", "org-a", 5000), String.class);

    assertEquals(HttpStatus.OK, response.getStatusCode());
    SecurityEvent event = latestEvent("audit:annotated-export");
    assertEquals("audit-export-2026", event.getResourceId());
    assertEquals("org-a", event.getOrgScope());
    assertEquals(5000L, event.getDataCount());
    assertEquals("HIGH", event.getAttribute("sensitivity"));
    assertEquals("EXPORT_COMPLETED", event.getReasonCode());
}

@Test
void letsForbiddenHttpStatusOverrideAnEnricherSuccess() {
    assertEquals(HttpStatus.FORBIDDEN,
        restTemplate.postForEntity(url("/audit/annotated-export-denied"), request("audit-export-2026", "org-a", 5000),
            String.class).getStatusCode());
    SecurityEvent event = latestEvent("audit:annotated-export-denied");
    assertEquals(SecurityEventResult.DENIED, event.getResult());
    assertEquals("HTTP_403", event.getReasonCode());
}
```

- [ ] **Step 2: Run the acceptance tests and confirm endpoints do not exist.**

Run: `mvn -pl integration-audit/spring2-web -am -Dtest=Spring2AuditWebAcceptanceTest test`

Expected: FAIL with 404 or missing annotated event.

Run: `mvn -pl integration-audit/spring3-web -am -Dtest=Spring3AuditWebAcceptanceTest test`

Expected: FAIL with 404 or missing annotated event.

- [ ] **Step 3: Add sample action and a host-owned enricher.**

Add these controller methods, keeping `AuditController` non-final so AOP can proxy it:

```java
@PostMapping("/annotated-export")
@MonitorAction(value = "audit:annotated-export", eventType = SecurityEventType.EXPORT,
    resourceType = "report", enrichers = AuditExportFacts.class)
@MonitorActionAttribute(name = "sensitivity", value = "HIGH")
public Map<String, Object> annotatedExport(
    @MonitorActionAttribute(target = MonitorActionAttributeTarget.RESOURCE_ID, path = "report.id")
    @MonitorActionAttribute(target = MonitorActionAttributeTarget.ORG_SCOPE, path = "tenant.code")
    @RequestBody AuditExportRequest request) {
    return exportResponse(request.getRows(), HttpStatus.OK);
}

@PostMapping("/annotated-export-denied")
@MonitorAction(value = "audit:annotated-export-denied", eventType = SecurityEventType.EXPORT,
    resourceType = "report", enrichers = AuditExportFacts.class)
public ResponseEntity<Map<String, Object>> annotatedExportDenied(
    @MonitorActionAttribute(target = MonitorActionAttributeTarget.RESOURCE_ID, path = "report.id")
    @RequestBody AuditExportRequest request) {
    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(exportResponse(request.getRows(), HttpStatus.FORBIDDEN));
}
```

Implement `AuditExportRequest` with nested `Report` and `Tenant` Java Bean values plus a requested row count, so acceptance uses a real `@RequestBody` binding. Implement `AuditExportFacts` as a `@Component` `MonitorActionEnricher`: it must not re-read resource ID or tenant from arguments; in `AFTER_RETURNING`, read `rowCount` from the returned map and return `dataCount(5000)`, `result(SUCCESS)` and `reasonCode("EXPORT_COMPLETED")`; in `AFTER_THROWING`, return no facts. Do not add raw request content or account identifiers.

- [ ] **Step 4: Run both acceptance modules and their parent reactor.**

Run: `mvn -pl integration-audit/spring2-web -am -Dtest=Spring2AuditWebAcceptanceTest test`

Expected: PASS.

Run: `mvn -pl integration-audit/spring3-web -am -Dtest=Spring3AuditWebAcceptanceTest test`

Expected: PASS.

Run: `mvn -pl integration-audit -am test`

Expected: PASS with both actual Servlet namespace variants.

- [ ] **Step 5: Commit the acceptance coverage.**

```bash
git add integration-audit/spring2-web integration-audit/spring3-web
git commit -m "test(integration): verify annotated action facts"
```

### Task 5: Document the Action-Code Support Matrix and Control Policy

**Files:**
- Modify: `docs/集成指南.md`

- [ ] **Step 1: Add the matrix and a documentation-level acceptance checklist.**

Insert a new subsection after current MVC annotation collection with a matrix containing these columns: suggested action code, `SecurityEventType`, built-in rules, host integration level, automatic annotation support, required additional facts and collection point, automatic control, and review requirement.

The rows must cover at least:

| Action family | Suggested code | Rules | Host level | Additional facts |
| --- | --- | --- | --- | --- |
| Authentication | `auth:login-success`, `auth:login-failure`, `auth:mfa-failure` | `AUTH-01` to `AUTH-03` | Required when capability exists | `attempted_account_hash`, `account_status` from parameters/return result. |
| Session | `session:concurrent` | `SESS-01` | Required when concurrent-session capability exists | `dataCount`, `different_networks` after evaluation. |
| Authorization | `access:denied`, `resource:read` | `AUTHZ-01`, `AUTHZ-02` | Required for resource-scope denial; optional for generic reads | `resourceId`, `sequential_access`. |
| Data | `data:query`, `data:sensitive-read`, `report:export` | `DATA-01` to `DATA-03`, `EXPT-01`, `EXPT-02` | Sensitive read/export required when capability exists | resource, count, sensitivity, work-hours, baseline ratio. |
| Privilege | `role:grant`, `admin:create` | `PRIV-01`, `PRIV-02` | Required when capability exists | target user, privilege increase, high-privilege marker. |
| Security operation | `security:rule-change`, `security:audit-config-change`, `security:switch-change` | `SECU-01` | Required when capability exists | none beyond trusted context. |

Add the exact control-policy table from the approved design: `RECORD` has no handler and no manual approval; CAPTCHA/rate-limit/session revoke/MFA/deny require an idempotent handler and run automatically only in `ENFORCE`; `REQUIRE_APPROVAL` creates a pending workflow and requires human approval; `LOCK` is custom-rule-only and defaults to manual review. State explicitly that every automatic action remains auditable and `OBSERVE` executes no host control.

- [ ] **Step 2: Add an end-to-end annotated enrichment example.**

```java
@PostMapping("/reports/export")
@MonitorAction(value = "report:export", eventType = SecurityEventType.EXPORT,
    resourceType = "report", enrichers = ReportExportFacts.class)
@MonitorActionAttribute(name = "sensitivity", value = "HIGH")
public ExportResult export(
    @MonitorActionAttribute(target = MonitorActionAttributeTarget.RESOURCE_ID, path = "report.id")
    @RequestBody ExportRequest request) {
    return reportService.export(request.getReport().getId());
}

@Component
final class ReportExportFacts implements MonitorActionEnricher {
    @Override
    public MonitorActionFacts enrich(MonitorActionInvocation invocation) {
        if (invocation.getPhase() == MonitorActionInvocation.Phase.BEFORE) {
            return MonitorActionFacts.empty();
        }
        if (invocation.getPhase() == MonitorActionInvocation.Phase.AFTER_RETURNING) {
            ExportResult result = (ExportResult) invocation.getReturnValue();
            return MonitorActionFacts.builder().dataCount(result.getRowCount())
                .reasonCode("EXPORT_COMPLETED").build();
        }
        return MonitorActionFacts.empty();
    }
}
```

Document that the controller must be proxyable (not final), an enricher must be a Spring Bean, static facts cannot be overridden by parameter mappings or dynamic enrichers, and a parameter path is supplemental evidence rather than an authorization decision. Document the restricted path grammar and that async/streaming/non-MVC operations continue with `ActionEventRecorder.draft(...)`.

- [ ] **Step 3: Check the documentation diff.**

Run: `git diff --check -- docs/集成指南.md`

Expected: no whitespace errors.

- [ ] **Step 4: Commit the guide update.**

```bash
git add docs/集成指南.md
git commit -m "docs: add action code support matrix"
```

### Task 6: Full Verification and Delivery Check

**Files:**
- Verify: all files from Tasks 1 through 5

- [ ] **Step 1: Inspect the final diff and required interfaces.**

Run: `git diff --check HEAD~5..HEAD`

Expected: no whitespace errors.

Run: `rg -n "MonitorActionAttribute|BoundParameterFactsExtractor|MonitorActionEnricher|attempted_account_hash|REQUIRE_APPROVAL" api core spring-support spring2-starter spring3-starter integration-audit docs/集成指南.md`

Expected: contracts, both Starter adapters, rule support, acceptance tests and guide matrix are all present.

- [ ] **Step 2: Run the full Maven reactor verification.**

Run: `mvn clean verify -DskipTests=false`

Expected: exit code 0 and all module tests pass.

- [ ] **Step 3: Review the result against the approved design.**

Confirm all of the following before final handoff:

- Static action properties work for annotations and registration, and cannot be overwritten by dynamic facts.
- The dynamic interface cannot mutate trusted identity/request/action fields.
- Parameter annotations use only the restricted Bean path grammar and can write only resource ID, org scope or non-sensitive attributes.
- Boot 2 uses `javax.servlet`; Boot 3 uses `jakarta.servlet`; both run real HTTP acceptance tests.
- HTTP exception/denial outcomes supersede business facts.
- Anonymous `AUTH-01`/`AUTH-02` use `attempted_account_hash` with safe fallback.
- The guide distinguishes required host integration, automatic execution and human approval, while retaining audit evidence for every control.

- [ ] **Step 4: Close verification findings in their owning task.**

Do not create a range-unknown verification commit. When verification exposes a defect, return to the task that owns the affected file, first add or correct its focused regression test, then repeat that task's focused command and create the task-specific commit using the exact file list shown above. Re-run this full verification task after every such correction.

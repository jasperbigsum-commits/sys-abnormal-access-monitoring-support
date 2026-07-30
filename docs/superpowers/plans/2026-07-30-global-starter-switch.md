# Global Starter Switch Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a backward-compatible `abnormal.access.monitor.enabled` switch that prevents all monitoring auto-configuration in the Boot 2.1, Boot 2.7, and Boot 3 starters when set to `false`.

**Architecture:** Each starter keeps its existing single top-level auto-configuration boundary. A class-level `@ConditionalOnProperty` disables that boundary before configuration properties, persistence requirements, web adapters, schedulers, or optional controls are created. The three version-specific property models and metadata files expose the same default-enabled contract.

**Tech Stack:** Java 8/17, Spring Boot auto-configuration, JUnit 5, AssertJ `ApplicationContextRunner`, Maven reactor, JSON configuration metadata, Markdown documentation.

---

## File Map

- Modify `spring2-legacy-starter/src/test/java/io/github/jasper/monitoring/spring2/autoconfigure/AbnormalAccessMonitorAutoConfigurationTest.java`: Boot 2.1 switch behavior.
- Modify `spring2-starter/src/test/java/io/github/jasper/monitoring/spring2/autoconfigure/AbnormalAccessMonitorAutoConfigurationTest.java`: Boot 2.7 switch behavior.
- Modify `spring3-starter/src/test/java/io/github/jasper/monitoring/spring3/autoconfigure/AbnormalAccessMonitorAutoConfigurationTest.java`: Boot 3 switch behavior.
- Modify the corresponding three `AbnormalAccessMonitorAutoConfiguration.java` files: class-level property condition.
- Modify the corresponding three `AbnormalAccessMonitorProperties.java` files: default-enabled property model.
- Modify the corresponding three `src/main/resources/META-INF/spring-configuration-metadata.json` files: IDE-visible property contract.
- Modify `docs/集成指南.md` and `docs/integration-guide.en.md`: operational behavior and security impact.

### Task 1: Specify Disabled Auto-Configuration Behavior

**Files:**
- Test: `spring2-legacy-starter/src/test/java/io/github/jasper/monitoring/spring2/autoconfigure/AbnormalAccessMonitorAutoConfigurationTest.java`
- Test: `spring2-starter/src/test/java/io/github/jasper/monitoring/spring2/autoconfigure/AbnormalAccessMonitorAutoConfigurationTest.java`
- Test: `spring3-starter/src/test/java/io/github/jasper/monitoring/spring3/autoconfigure/AbnormalAccessMonitorAutoConfigurationTest.java`

- [ ] **Step 1: Add the disabled-without-persistence test to all three starters**

Add this test to each class, using the package-local types already available in that starter:

```java
@Test
void disablesTheEntireStarterWithoutRequiringPersistence() {
    new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(AbnormalAccessMonitorAutoConfiguration.class))
        .withPropertyValues(
            "abnormal.access.monitor.enabled=false",
            "abnormal.access.monitor.instrumentation.enabled=true",
            "abnormal.access.monitor.ip-control.enabled=true")
        .run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(AbnormalAccessMonitorProperties.class);
            assertThat(context).doesNotHaveBean(MonitoringService.class);
            assertThat(context).doesNotHaveBean(ResourceAccessGuard.class);
        });
}
```

This simultaneously proves that the global switch wins over local switches and bypasses the normal `SqlSessionFactory` requirement.

- [ ] **Step 2: Run the focused tests and verify the new tests fail**

Run:

```powershell
mvn -pl spring2-legacy-starter,spring2-starter,spring3-starter -am -Dtest=AbnormalAccessMonitorAutoConfigurationTest "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Expected: all three new tests fail because the existing auto-configuration still attempts to start and requires persistence.

### Task 2: Implement the Global Condition and Property Model

**Files:**
- Modify: `spring2-legacy-starter/src/main/java/io/github/jasper/monitoring/spring2/autoconfigure/AbnormalAccessMonitorAutoConfiguration.java`
- Modify: `spring2-starter/src/main/java/io/github/jasper/monitoring/spring2/autoconfigure/AbnormalAccessMonitorAutoConfiguration.java`
- Modify: `spring3-starter/src/main/java/io/github/jasper/monitoring/spring3/autoconfigure/AbnormalAccessMonitorAutoConfiguration.java`
- Modify: `spring2-legacy-starter/src/main/java/io/github/jasper/monitoring/spring2/autoconfigure/AbnormalAccessMonitorProperties.java`
- Modify: `spring2-starter/src/main/java/io/github/jasper/monitoring/spring2/autoconfigure/AbnormalAccessMonitorProperties.java`
- Modify: `spring3-starter/src/main/java/io/github/jasper/monitoring/spring3/autoconfigure/AbnormalAccessMonitorProperties.java`

- [ ] **Step 1: Add the class-level condition in all three auto-configurations**

Reuse the existing `ConditionalOnProperty` import and add this annotation directly above `@EnableConfigurationProperties` (or directly below `@AutoConfiguration` in Boot 3):

```java
@ConditionalOnProperty(
    prefix = "abnormal.access.monitor",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
```

Do not add conditions to individual bean methods or nested configurations; the top-level condition must own the complete boundary.

- [ ] **Step 2: Add the default-enabled property to all three property classes**

Add the field before `systemId`:

```java
private boolean enabled = true;
```

Add accessors beside the existing top-level accessors:

```java
public boolean isEnabled() {
    return enabled;
}

public void setEnabled(boolean enabled) {
    this.enabled = enabled;
}
```

- [ ] **Step 3: Run the focused tests and verify they pass**

Run:

```powershell
mvn -pl spring2-legacy-starter,spring2-starter,spring3-starter -am -Dtest=AbnormalAccessMonitorAutoConfigurationTest "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Expected: `BUILD SUCCESS`; existing default-enabled tests still pass and each explicit-disabled context starts without monitoring beans.

- [ ] **Step 4: Commit runtime behavior and tests**

```powershell
git add -- spring2-legacy-starter/src/main/java spring2-legacy-starter/src/test/java spring2-starter/src/main/java spring2-starter/src/test/java spring3-starter/src/main/java spring3-starter/src/test/java
git commit -m "feat(starters): add global integration switch"
```

### Task 3: Publish Configuration Metadata

**Files:**
- Modify: `spring2-legacy-starter/src/main/resources/META-INF/spring-configuration-metadata.json`
- Modify: `spring2-starter/src/main/resources/META-INF/spring-configuration-metadata.json`
- Modify: `spring3-starter/src/main/resources/META-INF/spring-configuration-metadata.json`

- [ ] **Step 1: Add the property entry to all three metadata files**

Insert this as the first item in each `properties` array, changing only the surrounding JSON comma placement:

```json
{
  "name": "abnormal.access.monitor.enabled",
  "type": "java.lang.Boolean",
  "defaultValue": true,
  "description": "是否启用异常访问监控 Starter；关闭后不注册任何监控、Web、调度、通知、管理或控制组件。"
}
```

- [ ] **Step 2: Validate metadata syntax and parity**

Run:

```powershell
$metadataFiles = @(
  'spring2-legacy-starter/src/main/resources/META-INF/spring-configuration-metadata.json',
  'spring2-starter/src/main/resources/META-INF/spring-configuration-metadata.json',
  'spring3-starter/src/main/resources/META-INF/spring-configuration-metadata.json'
)
$metadataFiles | ForEach-Object { Get-Content -Raw $_ | ConvertFrom-Json | Out-Null }
rg -n 'abnormal.access.monitor.enabled' spring2-legacy-starter spring2-starter spring3-starter
```

Expected: JSON parsing produces no errors and `rg` reports one metadata definition plus runtime/test references in each starter.

- [ ] **Step 3: Commit metadata**

```powershell
git add -- spring2-legacy-starter/src/main/resources/META-INF/spring-configuration-metadata.json spring2-starter/src/main/resources/META-INF/spring-configuration-metadata.json spring3-starter/src/main/resources/META-INF/spring-configuration-metadata.json
git commit -m "docs(starters): publish global switch metadata"
```

### Task 4: Document Integration and Operational Impact

**Files:**
- Modify: `docs/集成指南.md`
- Modify: `docs/integration-guide.en.md`

- [ ] **Step 1: Add the Chinese installation note after the installation checklist**

````markdown
### 全局启停

Starter 默认启用，未配置 `abnormal.access.monitor.enabled` 时保持现有装配行为。尚未完成集成或需要按环境隔离组件时可显式关闭：

```yaml
abnormal:
  access:
    monitor:
      enabled: false
```

关闭后 Starter 不注册监控运行时、Servlet/AOP 集成、管理服务、通知调度或 IP 控制，也不要求 `SqlSessionFactory` 和宿主 SPI。该开关在应用启动时判定，不能动态切换。生产环境关闭后不会产生本组件的安全事件、告警或控制，请同步调整监控与审计预期。
````

- [ ] **Step 2: Add the equivalent English installation note**

````markdown
### Global Enablement

The starter is enabled by default. Set `abnormal.access.monitor.enabled=false` to keep the entire integration out of an application context that is not ready to provide persistence or host SPIs.

```yaml
abnormal:
  access:
    monitor:
      enabled: false
```

When disabled, the starter registers no monitoring runtime, Servlet/AOP integration, management services, notification scheduling, or IP controls, and it does not require a `SqlSessionFactory`. The property is evaluated only at application startup. A disabled production deployment emits no events, alerts, or controls from this component, so monitoring and audit expectations must be adjusted accordingly.
````

- [ ] **Step 3: Check documentation references and formatting**

Run:

```powershell
rg -n "abnormal\.access\.monitor\.enabled|全局启停|Global Enablement" docs/集成指南.md docs/integration-guide.en.md
git diff --check
```

Expected: both guides describe the same default, disabled boundary, startup-only behavior, and security impact; `git diff --check` is clean.

- [ ] **Step 4: Commit documentation**

```powershell
git add -- docs/集成指南.md docs/integration-guide.en.md
git commit -m "docs(integration): explain global starter switch"
```

### Task 5: Verify the Reactor

**Files:**
- Verify only; no planned file changes.

- [ ] **Step 1: Run all starter tests**

```powershell
mvn -pl spring2-legacy-starter,spring2-starter,spring3-starter -am test
```

Expected: `BUILD SUCCESS` with Boot 2.1, Boot 2.7, and Boot 3 parity.

- [ ] **Step 2: Run the full reactor verification**

```powershell
mvn clean verify -DskipTests=false
```

Expected: `BUILD SUCCESS` and no failed tests.

- [ ] **Step 3: Inspect the final repository state**

```powershell
git status --short
git log -5 --oneline
```

Expected: no uncommitted implementation files; recent commits show runtime behavior, metadata, documentation, and the approved design.

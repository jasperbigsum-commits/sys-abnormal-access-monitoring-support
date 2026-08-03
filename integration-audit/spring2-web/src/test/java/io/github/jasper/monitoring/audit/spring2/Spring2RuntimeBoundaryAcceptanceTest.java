package io.github.jasper.monitoring.audit.spring2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.jasper.monitoring.api.ControlActionType;
import io.github.jasper.monitoring.api.ControlTrigger;
import io.github.jasper.monitoring.api.action.ActionCatalog;
import io.github.jasper.monitoring.api.action.BuiltInActions;
import io.github.jasper.monitoring.api.control.ControlCatalog;
import io.github.jasper.monitoring.api.control.ControlType;
import io.github.jasper.monitoring.api.error.MonitoringConfigurationException;
import io.github.jasper.monitoring.api.error.MonitoringValidationException;
import io.github.jasper.monitoring.api.fact.ActionFacts;
import io.github.jasper.monitoring.api.fact.BuiltInFacts;
import io.github.jasper.monitoring.api.fact.FactBinding;
import io.github.jasper.monitoring.api.fact.FactCatalog;
import io.github.jasper.monitoring.api.fact.FactSource;
import io.github.jasper.monitoring.api.rule.RuleCatalog;
import io.github.jasper.monitoring.core.application.DefaultMonitoringRuntime;
import io.github.jasper.monitoring.core.application.control.AnnotatedControlHandler;
import io.github.jasper.monitoring.core.domain.ControlCommand;
import io.github.jasper.monitoring.core.port.ControlHandler;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Boot 2 的运行时边界与启动期契约验收测试。
 *
 * <p>验证 Action/Fact/规则目录冻结、Fact 绑定所有权、控制触发器声明、ENFORCE 能力覆盖，
 * 以及 Boot 2/3 验收编号集合的完全对称。</p>
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:audit-spring2-boundary;MODE=MySQL;DB_CLOSE_DELAY=0")
class Spring2RuntimeBoundaryAcceptanceTest {
    private static final Pattern ACCEPTANCE_ID = Pattern.compile("@(DisplayName)\\(\\\"(TC|IA)-([0-9]{2})");

    @Autowired
    private ActionCatalog actions;
    @Autowired
    private FactCatalog facts;
    @Autowired
    private RuleCatalog rules;
    @Autowired
    private ControlCatalog<ControlHandler> controls;

    @Test
    @DisplayName("IA-06 fact bindings have unique frozen applicability")
    void ia06_factBindingsHaveUniqueFrozenApplicability() {
        FactBinding exact = FactBinding.forAction(BuiltInActions.ReportExport.class,
            FactSource.HOST_PROVIDER, execution -> dataCount(), BuiltInFacts.DataCount.class);
        FactBinding inherited = FactBinding.forContract(BuiltInActions.ExportContract.class,
            FactSource.HOST_PROVIDER, execution -> dataCount(), BuiltInFacts.DataCount.class);

        assertTrue(exact.appliesTo(BuiltInActions.ReportExport.class));
        assertTrue(inherited.appliesTo(BuiltInActions.ReportExport.class));
        assertThrows(MonitoringConfigurationException.class,
            () -> new DefaultMonitoringRuntime(actions, facts, Arrays.asList(exact, inherited)));
        assertThrows(MonitoringConfigurationException.class,
            () -> actions.register(BuiltInActions.Query.class, actions.require(BuiltInActions.Query.class)));
    }

    @Test
    @DisplayName("IA-08 control trigger scanner rejects invalid and duplicate bindings")
    void ia08_controlTriggerScannerRejectsInvalidAndDuplicateBindings() {
        assertThrows(MonitoringValidationException.class,
            () -> AnnotatedControlHandler.hasBindings(InvalidTrigger.class));
        assertThrows(MonitoringConfigurationException.class,
            () -> AnnotatedControlHandler.hasBindings(DuplicateTrigger.class));
    }

    @Test
    @DisplayName("IA-09 enforce mode warns and returns undefined for missing control handlers")
    void ia09_enforceModeReturnsUndefinedForMissingControlHandlers() {
        io.github.jasper.monitoring.spring2.autoconfigure.AbnormalAccessMonitorProperties properties =
            new io.github.jasper.monitoring.spring2.autoconfigure.AbnormalAccessMonitorProperties();
        properties.setMode(io.github.jasper.monitoring.api.MonitoringMode.ENFORCE);
        io.github.jasper.monitoring.core.application.control.ControlHandlerRegistry fallbackOnly =
            new io.github.jasper.monitoring.core.application.control.ControlHandlerRegistry(
                java.util.Collections.<ControlHandler>emptyList(),
                io.github.jasper.monitoring.core.application.control.DefaultControlActionTrigger.defaults());
        ControlCatalog<ControlHandler> fallbackControls =
            new io.github.jasper.monitoring.spring2.autoconfigure.AbnormalAccessMonitorAutoConfiguration()
                .abnormalAccessControlCatalog(fallbackOnly, properties, rules);
        Set<ControlType> required = rules.requiredControlTypes();
        assertFalse(required.isEmpty());
        for (ControlType type : required) {
            ControlHandler fallback = fallbackControls.require(type);
            ControlActionType action = ControlActionType.valueOf(type.name());
            assertTrue(fallback.isFallback());
            assertEquals(io.github.jasper.monitoring.api.control.ControlStatus.UNDEFINED,
                fallback.execute(new ControlCommand("test-system", "missing:" + type.name(), "alert", "subject",
                    action, null, "rule")).getStatus());
            ControlHandler handler = controls.require(type);
            assertFalse(handler.isFallback());
            assertTrue(handler.supports(action));
        }
    }

    @Test
    @DisplayName("IA-10 frozen rule catalog owns runtime control coverage")
    void ia10_frozenRuleCatalogOwnsRuntimeControlCoverage() {
        assertTrue(actions.isFrozen());
        assertTrue(facts.isFrozen());
        assertTrue(rules.isFrozen());
        assertEquals(rules.requiredControlTypes(), controls.handlers().keySet());
    }

    @Test
    @DisplayName("IA-12 Boot 2 and Boot 3 expose the exact acceptance ID set")
    void ia12_boot2AndBoot3ExposeExactAcceptanceIdSet() throws IOException {
        Path auditRoot = Paths.get("").toAbsolutePath().getParent();
        Set<String> boot2 = acceptanceIds(auditRoot.resolve("spring2-web/src/test/java"));
        Set<String> boot3 = acceptanceIds(auditRoot.resolve("spring3-web/src/test/java"));
        assertEquals(expectedIds(), boot2);
        assertEquals(boot2, boot3);
    }

    private static ActionFacts dataCount() {
        return ActionFacts.builder().put(BuiltInFacts.DataCount.class, Long.valueOf(1L)).build();
    }

    private static Set<String> acceptanceIds(Path root) throws IOException {
        Set<String> ids = new LinkedHashSet<String>();
        try (Stream<Path> paths = Files.walk(root)) {
            paths.filter(Files::isRegularFile).filter(path -> path.toString().endsWith("AcceptanceTest.java"))
                .forEach(path -> collectIds(path, ids));
        }
        return ids;
    }

    private static void collectIds(Path path, Set<String> ids) {
        try {
            Matcher matcher = ACCEPTANCE_ID.matcher(
                new String(Files.readAllBytes(path), StandardCharsets.UTF_8));
            while (matcher.find()) {
                ids.add(matcher.group(2) + "-" + matcher.group(3));
            }
        } catch (IOException failure) {
            throw new IllegalStateException(failure);
        }
    }

    private static Set<String> expectedIds() {
        Set<String> ids = new LinkedHashSet<String>();
        for (int index = 1; index <= 18; index++) {
            ids.add(String.format("TC-%02d", Integer.valueOf(index)));
        }
        for (int index = 1; index <= 12; index++) {
            ids.add(String.format("IA-%02d", Integer.valueOf(index)));
        }
        return ids;
    }

    public static final class InvalidTrigger {
        @ControlTrigger(ControlActionType.DENY)
        public void deny(String invalid) {
        }
    }

    public static final class DuplicateTrigger {
        @ControlTrigger(ControlActionType.DENY)
        public void first(ControlCommand command) {
        }

        @ControlTrigger(ControlActionType.DENY)
        public void second(ControlCommand command) {
        }
    }
}

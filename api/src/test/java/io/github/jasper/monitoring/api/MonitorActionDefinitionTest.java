package io.github.jasper.monitoring.api;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

class MonitorActionDefinitionTest {

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

    @Test
    void rejectsDynamicDeclarationsOnAnActionElement() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> MonitorActionDefinition.from(
            DeclaredActions.class.getMethod("dynamicDeclaration")));
    }

    @Test
    void rejectsWhitespaceOnlyStaticAttributePaths() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> MonitorActionDefinition.from(
            DeclaredActions.class.getMethod("whitespacePath")));
    }

    @Test
    void validatesAllowedParameterFactDeclarations() throws Exception {
        Method method = DeclaredActions.class.getMethod("exportRequest", ExportRequest.class);
        MonitorActionAttribute resourceId = method.getParameters()[0]
            .getAnnotation(MonitorActionAttribute.class);

        assertEquals(MonitorActionAttributeTarget.RESOURCE_ID, resourceId.target());
        assertEquals("report.id", resourceId.path());
        assertDoesNotThrow(() -> MonitorActionDefinition.validateParameterAttribute(resourceId));
    }

    @Test
    void rejectsStaticValuesInParameterFactDeclarations() throws Exception {
        Method method = DeclaredActions.class.getMethod("invalidRequest", String.class);
        MonitorActionAttribute attribute = method.getParameters()[0]
            .getAnnotation(MonitorActionAttribute.class);

        assertThrows(IllegalArgumentException.class,
            () -> MonitorActionDefinition.validateParameterAttribute(attribute));
    }

    @Test
    void rejectsWhitespaceOnlyValuesInParameterFactDeclarations() throws Exception {
        Method method = DeclaredActions.class.getMethod("whitespaceValueRequest", String.class);
        MonitorActionAttribute attribute = method.getParameters()[0]
            .getAnnotation(MonitorActionAttribute.class);

        assertThrows(IllegalArgumentException.class,
            () -> MonitorActionDefinition.validateParameterAttribute(attribute));
    }

    @Test
    void preservesStaticAttributesWhenCopiedToABuilder() {
        MonitorActionDefinition definition = MonitorActionDefinition.builder("report:export")
            .eventType(SecurityEventType.EXPORT)
            .attribute("sensitivity", "HIGH")
            .build();

        MonitorActionDefinition copy = definition.toBuilder().build();

        assertEquals(definition, copy);
        assertEquals(definition.hashCode(), copy.hashCode());
        assertEquals("HIGH", copy.getAttributes().get("sensitivity"));
    }

    @Test
    void keepsAnnotationOnlyResolutionSourceCompatible() throws Exception {
        MonitorAction action = DeclaredActions.class.getMethod("export", String.class)
            .getAnnotation(MonitorAction.class);

        assertTrue(MonitorActionDefinition.from(action).getAttributes().isEmpty());
    }

    private static final class DeclaredActions {
        @MonitorAction(value = "report:export", eventType = SecurityEventType.EXPORT)
        @MonitorActionAttribute(name = "sensitivity", value = "HIGH")
        @MonitorActionAttribute(name = "high_privilege", value = "true")
        public void export(String reportId) {
        }

        @MonitorAction("report:invalid")
        @MonitorActionAttribute(name = "monitor.rule-tag.manual", value = "true")
        public void invalid() {
        }

        @MonitorAction("report:dynamic")
        @MonitorActionAttribute(target = MonitorActionAttributeTarget.RESOURCE_ID, value = "report-1")
        public void dynamicDeclaration() {
        }

        @MonitorAction("report:whitespace-path")
        @MonitorActionAttribute(name = "sensitivity", value = "HIGH", path = " ")
        public void whitespacePath() {
        }

        public void exportRequest(
            @MonitorActionAttribute(target = MonitorActionAttributeTarget.RESOURCE_ID, path = "report.id")
            ExportRequest request) {
        }

        public void invalidRequest(
            @MonitorActionAttribute(name = "sensitivity", value = "HIGH") String value) {
        }

        public void whitespaceValueRequest(
            @MonitorActionAttribute(name = "sensitivity", value = " ") String value) {
        }
    }

    private static final class ExportRequest {
        private final Report report = new Report();

        public Report getReport() {
            return report;
        }
    }

    private static final class Report {
        public String getId() {
            return "report-1";
        }
    }
}

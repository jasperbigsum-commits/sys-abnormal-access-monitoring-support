package io.github.jasper.monitoring.api.fact;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;

class ActionFactsTest {

    @Test
    void storesAndReadsFactsWithTheirDeclaredValueTypes() {
        ActionFacts facts = ActionFacts.builder()
            .put(DataCountFact.class, 5L)
            .build();

        Long dataCount = facts.get(DataCountFact.class);

        assertEquals(Long.valueOf(5L), dataCount);
    }

    @Test
    void exposesAnImmutableClassKeyedMap() {
        ActionFacts facts = ActionFacts.builder()
            .put(DataCountFact.class, 5L)
            .build();
        Map<Class<? extends FactType<?>>, Object> values = facts.asMap();

        assertEquals(Long.valueOf(5L), values.get(DataCountFact.class));
        assertThrows(UnsupportedOperationException.class,
            () -> values.put(TextFact.class, "changed"));
    }

    @Test
    void rejectsNullFactTypesAndValues() {
        assertThrows(NullPointerException.class,
            () -> ActionFacts.builder().put(null, 5L));
        assertThrows(NullPointerException.class,
            () -> ActionFacts.builder().put(DataCountFact.class, null));
    }

    @Test
    void rejectsStaticallyMismatchedValuesAtCompilation() {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "Tests must run on a JDK");
        DiagnosticCollector<JavaFileObject> validDiagnostics = new DiagnosticCollector<JavaFileObject>();
        JavaFileObject validSource = factPutSource("ValidFactPut", "5000L");
        Boolean validCompiled = compiler.getTask(null, null, validDiagnostics,
            Arrays.asList("-classpath", System.getProperty("java.class.path"), "-proc:none"),
            null, Collections.singletonList(validSource)).call();
        assertTrue(validCompiled.booleanValue(), () -> diagnosticsText(validDiagnostics));

        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<JavaFileObject>();
        JavaFileObject source = factPutSource("InvalidFactPut", "\"5000\"");

        Boolean compiled = compiler.getTask(null, null, diagnostics,
            Arrays.asList("-classpath", System.getProperty("java.class.path"), "-proc:none"),
            null, Collections.singletonList(source)).call();

        assertFalse(compiled.booleanValue(), () -> diagnosticsText(diagnostics));
        assertTrue(diagnostics.getDiagnostics().stream()
            .anyMatch(diagnostic -> diagnostic.getKind() == Diagnostic.Kind.ERROR));
    }

    @Test
    void exposesTypedParameterBindingsAtRuntime() throws Exception {
        Method method = DeclaredActions.class.getDeclaredMethod("export", Request.class);
        Parameter parameter = method.getParameters()[0];

        ActionFact annotation = parameter.getAnnotation(ActionFact.class);

        assertEquals(TextFact.class, annotation.value());
        assertEquals("report.id", annotation.path());
    }

    public static final class DataCountFact implements FactType<Long> {
    }

    public static final class TextFact implements FactType<String> {
    }

    private static final class DeclaredActions {
        void export(@ActionFact(value = TextFact.class, path = "report.id") Request request) {
        }
    }

    private static final class Request {
    }

    private static JavaFileObject source(String className, String content) {
        return new SimpleJavaFileObject(
            URI.create("string:///" + className + JavaFileObject.Kind.SOURCE.extension),
            JavaFileObject.Kind.SOURCE) {
            @Override
            public CharSequence getCharContent(boolean ignoreEncodingErrors) {
                return content;
            }
        };
    }

    private static JavaFileObject factPutSource(String className, String valueExpression) {
        return source(className, String.join("\n",
            "import io.github.jasper.monitoring.api.fact.ActionFacts;",
            "import io.github.jasper.monitoring.api.fact.FactType;",
            "final class " + className + " {",
            "  static final class DataCountFact implements FactType<Long> {}",
            "  void add() {",
            "    ActionFacts.builder().put(DataCountFact.class, " + valueExpression + ");",
            "  }",
            "}"));
    }

    private static String diagnosticsText(DiagnosticCollector<JavaFileObject> diagnostics) {
        StringBuilder text = new StringBuilder();
        for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics.getDiagnostics()) {
            text.append(diagnostic.getMessage(null)).append(System.lineSeparator());
        }
        return text.toString();
    }
}

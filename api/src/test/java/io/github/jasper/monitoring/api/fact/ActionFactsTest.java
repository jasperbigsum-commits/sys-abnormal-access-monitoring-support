package io.github.jasper.monitoring.api.fact;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
    void rejectsAValueWhoseRuntimeTypeDoesNotMatchTheFactType() {
        assertThrows(IllegalArgumentException.class,
            () -> ActionFacts.builder().putRaw(DataCountFact.class, "5000").build());
    }

    @Test
    void rejectsNullFactTypesValuesAndMissingValueTypeDeclarations() {
        assertThrows(NullPointerException.class,
            () -> ActionFacts.builder().putRaw(null, 5L));
        assertThrows(NullPointerException.class,
            () -> ActionFacts.builder().put(DataCountFact.class, null));
        assertThrows(IllegalArgumentException.class,
            () -> ActionFacts.builder().putRaw(rawFactType(), "value"));
    }

    @Test
    void reusesResolvedMetadataForRepeatedPutsWithoutConstructingFactTypes() {
        ActionFacts facts = ActionFacts.builder()
            .put(ThrowingConstructorFact.class, 1L)
            .put(ThrowingConstructorFact.class, 2L)
            .build();

        assertEquals(Long.valueOf(2L), facts.get(ThrowingConstructorFact.class));
    }

    @Test
    void rejectsStaticallyMismatchedValuesAtCompilation() {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "Tests must run on a JDK");
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<JavaFileObject>();
        JavaFileObject source = source("InvalidFactPut", String.join("\n",
            "import io.github.jasper.monitoring.api.fact.ActionFacts;",
            "import io.github.jasper.monitoring.api.fact.FactType;",
            "final class InvalidFactPut {",
            "  static final class DataCountFact implements FactType<Long> {",
            "    public Class<Long> valueType() { return Long.class; }",
            "  }",
            "  void add() {",
            "    ActionFacts.builder().put(DataCountFact.class, \"5000\");",
            "  }",
            "}"));

        Boolean compiled = compiler.getTask(null, null, diagnostics,
            Arrays.asList("-classpath", System.getProperty("java.class.path"), "-proc:none"),
            null, Collections.singletonList(source)).call();

        assertFalse(compiled.booleanValue(), () -> diagnosticsText(diagnostics));
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
        @Override
        public Class<Long> valueType() {
            return Long.class;
        }
    }

    public static final class TextFact implements FactType<String> {
        @Override
        public Class<String> valueType() {
            return String.class;
        }
    }

    private interface LongFactType extends FactType<Long> {
    }

    private static final class ThrowingConstructorFact implements LongFactType {
        private ThrowingConstructorFact() {
            throw new AssertionError("Fact types must not be constructed to resolve metadata");
        }

        @Override
        public Class<Long> valueType() {
            return Long.class;
        }
    }

    @SuppressWarnings("rawtypes")
    private static final class RawFact implements FactType {
        @Override
        public Class valueType() {
            return String.class;
        }
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

    private static String diagnosticsText(DiagnosticCollector<JavaFileObject> diagnostics) {
        StringBuilder text = new StringBuilder();
        for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics.getDiagnostics()) {
            text.append(diagnostic.getMessage(null)).append(System.lineSeparator());
        }
        return text.toString();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Class<? extends FactType<?>> rawFactType() {
        return (Class) RawFact.class;
    }
}

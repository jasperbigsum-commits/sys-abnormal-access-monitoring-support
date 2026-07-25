package io.github.jasper.monitoring.api.fact;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Map;
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
    void rejectsNullFactTypesValuesAndDeclaredValueTypes() {
        assertThrows(NullPointerException.class,
            () -> ActionFacts.builder().putRaw(null, 5L));
        assertThrows(NullPointerException.class,
            () -> ActionFacts.builder().put(DataCountFact.class, null));
        assertThrows(NullPointerException.class,
            () -> ActionFacts.builder().put(NullValueTypeFact.class, "value"));
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

    public static final class NullValueTypeFact implements FactType<String> {
        @Override
        public Class<String> valueType() {
            return null;
        }
    }

    private static final class DeclaredActions {
        void export(@ActionFact(value = TextFact.class, path = "report.id") Request request) {
        }
    }

    private static final class Request {
    }
}

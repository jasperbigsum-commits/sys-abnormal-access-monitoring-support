package io.github.jasper.monitoring.spring.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.jasper.monitoring.api.fact.BuiltInFacts;
import io.github.jasper.monitoring.api.fact.FactCatalog;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ActionFactExtractorTest {
    private final ActionFactExtractor extractor = new ActionFactExtractor(facts());

    @Test
    void extractsDirectGetterFieldAndListIndex() {
        ExportRequest request = new ExportRequest(" report-7 ",
            Arrays.asList(new Item(3L), new Item(42L)));

        assertEquals("report-7", extractor.extract(request, "resourceId",
            BuiltInFacts.ResourceId.class));
        assertEquals(Long.valueOf(42L), extractor.extract(request, "items[1].count",
            BuiltInFacts.DataCount.class));
        assertEquals(Long.valueOf(9L), extractor.extract(new PublicCount(), "count",
            BuiltInFacts.DataCount.class));
        assertEquals(Long.valueOf(7L), extractor.extract(Long.valueOf(7L), "",
            BuiltInFacts.DataCount.class));
    }

    @Test
    void returnsNullWhenTheRootOrIntermediateValueIsNull() {
        assertNull(extractor.extract(null, "resourceId", BuiltInFacts.ResourceId.class));
        assertNull(extractor.extract(new ExportRequest(null, null), "items[0].count",
            BuiltInFacts.DataCount.class));
    }

    @Test
    void rejectsTypeMismatchAndUnboundedPathFeatures() {
        assertThrows(IllegalArgumentException.class, () -> extractor.extract("42", "",
            BuiltInFacts.DataCount.class));
        assertThrows(IllegalArgumentException.class, () -> extractor.extract(new ExportRequest(null, null),
            "class", BuiltInFacts.ResourceId.class));
        assertThrows(IllegalArgumentException.class, () -> extractor.extract(new ExportRequest(null, null),
            "getResourceId()", BuiltInFacts.ResourceId.class));
        Map<String, String> map = Collections.singletonMap("resourceId", "report-7");
        assertThrows(IllegalArgumentException.class, () -> extractor.extract(map, "resourceId",
            BuiltInFacts.ResourceId.class));
        assertThrows(IllegalArgumentException.class, () -> extractor.extract(
            new ExportRequest(null, Collections.singletonList(new Item(1L))), "items[-1].count",
            BuiltInFacts.DataCount.class));
    }

    private static FactCatalog facts() {
        FactCatalog catalog = new FactCatalog();
        BuiltInFacts.registerInto(catalog);
        catalog.freeze();
        return catalog;
    }

    public static final class ExportRequest {
        private final String resourceId;
        private final List<Item> items;
        ExportRequest(String resourceId, List<Item> items) {
            this.resourceId = resourceId;
            this.items = items;
        }
        public String getResourceId() { return resourceId; }
        public List<Item> getItems() { return items; }
    }

    public static final class Item {
        private final Long count;
        Item(Long count) { this.count = count; }
        public Long getCount() { return count; }
    }

    public static final class PublicCount {
        public Long count = 9L;
    }
}

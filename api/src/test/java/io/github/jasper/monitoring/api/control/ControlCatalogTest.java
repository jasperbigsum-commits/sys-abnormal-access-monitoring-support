package io.github.jasper.monitoring.api.control;

import java.util.Collections;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ControlCatalogTest {
    @Test void enforceChecksOnlyEnabledControlTypes() {
        Object handler = new Object();
        ControlCatalog<Object> catalog = ControlCatalog.<Object>builder()
            .enforce(Collections.singleton(ControlType.LOCK)).bind(ControlType.LOCK, handler).freeze();
        assertSame(handler, catalog.require(ControlType.LOCK));
    }

    @Test void enforceRejectsMissingEnabledType() {
        assertThrows(IllegalStateException.class, () -> ControlCatalog.<Object>builder()
            .enforce(Collections.singleton(ControlType.LOCK)).freeze());
    }
}

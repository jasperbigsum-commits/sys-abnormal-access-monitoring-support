package io.github.jasper.monitoring.spring.support.management;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Clock;
import org.junit.jupiter.api.Test;

class ManagementServiceFactoryTest {
    @Test void requiresTrustedAuthorizer() {
        assertThrows(NullPointerException.class,()->ManagementServiceFactory.create(null,null,null,null,Clock.systemUTC()));
    }
}

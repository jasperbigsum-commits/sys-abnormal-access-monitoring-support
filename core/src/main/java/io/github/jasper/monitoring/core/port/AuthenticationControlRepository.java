package io.github.jasper.monitoring.core.port;

import io.github.jasper.monitoring.core.domain.ControlCommand;
import java.time.Instant;
import java.util.List;

/** Narrow lookup boundary used by authentication pre-checks. */
public interface AuthenticationControlRepository {
    List<ControlCommand> findActive(String systemId, String subject, Instant at);
}

package io.github.jasper.monitoring.core.port;

import io.github.jasper.monitoring.core.domain.WhitelistEntry;
import java.time.Instant;

/** Persistence boundary for time-bounded rule exemptions. */
public interface WhitelistRepository {
    boolean isActive(String ruleId, String subject, Instant at);
    void add(WhitelistEntry entry);
}

package io.github.jasper.monitoring.mybatis.po;

import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/** Persistent representation of one observe-only rule match. */
@Getter
@Setter
public final class RuleObservationPo {
    private String observationId;
    private String ruleId;
    private String eventId;
    private String subject;
    private Instant observedAt;
}

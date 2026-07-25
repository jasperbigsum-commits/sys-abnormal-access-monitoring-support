package io.github.jasper.monitoring.spring.support.control;

import io.github.jasper.monitoring.api.ControlActionType;
import io.github.jasper.monitoring.core.domain.ControlCommand;
import io.github.jasper.monitoring.core.domain.ControlExecution;
import io.github.jasper.monitoring.core.port.ControlHandler;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Opt-in handler for rule-allowlisted controls whose IP-literal subject is normalized to a canonical state key.
 */
public final class GenericIpControlHandler implements ControlHandler {
    private static final String RULE_NOT_ALLOWED = "GENERIC_IP_RULE_NOT_ALLOWED";
    private static final String SUBJECT_REQUIRED = "GENERIC_IP_SUBJECT_REQUIRED";
    private static final String LITERAL_REQUIRED = "GENERIC_IP_LITERAL_REQUIRED";
    private static final String EXPIRED = "GENERIC_IP_CONTROL_EXPIRED";
    private static final String TTL_EXCEEDED = "GENERIC_IP_CONTROL_TTL_EXCEEDED";
    private static final String UNSUPPORTED = "GENERIC_IP_ACTION_UNSUPPORTED";
    private static final String CAPACITY_REJECTED = "GENERIC_IP_CONTROL_CAPACITY_REJECTED";
    private static final String STATE_FAILED = "GENERIC_IP_CONTROL_STATE_FAILED";

    private final IpControlState state;
    private final Set<String> allowedRuleIds;
    private final Duration maxTtl;
    private final Clock clock;

    public GenericIpControlHandler(IpControlState state, Set<String> allowedRuleIds,
                                   Duration maxTtl, Clock clock) {
        if (state == null) {
            throw new NullPointerException("state");
        }
        if (allowedRuleIds == null) {
            throw new NullPointerException("allowedRuleIds");
        }
        if (allowedRuleIds.isEmpty()) {
            throw new IllegalArgumentException("allowedRuleIds must not be empty");
        }
        Set<String> rules = new HashSet<String>();
        for (String ruleId : allowedRuleIds) {
            if (ruleId == null || ruleId.trim().isEmpty()) {
                throw new IllegalArgumentException("allowedRuleIds must contain only non-blank values");
            }
            rules.add(ruleId);
        }
        if (maxTtl == null || maxTtl.isZero() || maxTtl.isNegative()) {
            throw new IllegalArgumentException("maxTtl must be positive");
        }
        if (clock == null) {
            throw new NullPointerException("clock");
        }
        this.state = state;
        this.allowedRuleIds = Collections.unmodifiableSet(rules);
        this.maxTtl = maxTtl;
        this.clock = clock;
    }

    @Override
    public boolean supports(ControlActionType action) {
        return action == ControlActionType.RATE_LIMIT || action == ControlActionType.DENY;
    }

    @Override
    public ControlExecution execute(ControlCommand command) {
        String key = command == null ? null : command.getIdempotencyKey();
        if (command == null || !supports(command.getAction())) {
            return ControlExecution.skipped(key, UNSUPPORTED);
        }
        if (!allowedRuleIds.contains(command.getRuleId())) {
            return ControlExecution.skipped(key, RULE_NOT_ALLOWED);
        }
        String subject = command.getSubject();
        if (subject == null || !subject.startsWith("ip:")) {
            return ControlExecution.skipped(key, SUBJECT_REQUIRED);
        }
        String canonicalIp = IpAddressLiteral.canonicalize(subject.substring(3));
        if (canonicalIp == null) {
            return ControlExecution.skipped(key, LITERAL_REQUIRED);
        }

        Instant now = clock.instant();
        Instant expiresAt = command.getExpiresAt();
        if (expiresAt == null || !expiresAt.isAfter(now)) {
            return ControlExecution.skipped(key, EXPIRED);
        }
        if (Duration.between(now, expiresAt).compareTo(maxTtl) > 0) {
            return ControlExecution.skipped(key, TTL_EXCEEDED);
        }

        try {
            IpControlState.ActivationResult result = state.activate(key, canonicalIp, command.getAction(),
                expiresAt, now);
            if (result == IpControlState.ActivationResult.ACTIVATED) {
                return ControlExecution.succeeded(key);
            }
            if (result == IpControlState.ActivationResult.IDEMPOTENT_REPLAY) {
                return ControlExecution.succeeded(key).replay();
            }
            if (result == IpControlState.ActivationResult.EXPIRED) {
                return ControlExecution.skipped(key, EXPIRED);
            }
            if (result == IpControlState.ActivationResult.CAPACITY_REJECTED) {
                return ControlExecution.failed(key, CAPACITY_REJECTED);
            }
            return ControlExecution.failed(key, STATE_FAILED);
        } catch (RuntimeException failure) {
            return ControlExecution.failed(key, STATE_FAILED);
        }
    }

}

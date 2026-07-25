package io.github.jasper.monitoring.spring.support.control;

import io.github.jasper.monitoring.api.ControlActionType;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * Bounded, single-JVM IP control state with lazy expiry cleanup.
 */
public final class LocalIpControlState implements IpControlState {
    private final int capacity;
    private final int permitsPerWindow;
    private final Duration fixedWindow;
    private final Map<String, ActiveControl> controlsByKey = new HashMap<String, ActiveControl>();
    private final Map<String, Set<String>> controlKeysByIp = new HashMap<String, Set<String>>();
    private final Map<String, RateWindow> rateWindowsByIp = new HashMap<String, RateWindow>();

    public LocalIpControlState(int capacity, int permitsPerWindow, Duration fixedWindow) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be positive");
        }
        if (permitsPerWindow <= 0) {
            throw new IllegalArgumentException("permitsPerWindow must be positive");
        }
        if (fixedWindow == null || fixedWindow.isZero() || fixedWindow.isNegative()) {
            throw new IllegalArgumentException("fixedWindow must be positive");
        }
        this.capacity = capacity;
        this.permitsPerWindow = permitsPerWindow;
        this.fixedWindow = fixedWindow;
    }

    @Override
    public synchronized ActivationResult activate(String idempotencyKey, String canonicalIp,
                                                   ControlActionType action, Instant expiresAt,
                                                   Instant now) {
        requireText(idempotencyKey, "idempotencyKey");
        requireText(canonicalIp, "canonicalIp");
        requireSupportedAction(action);
        if (expiresAt == null) {
            throw new IllegalArgumentException("expiresAt must not be null");
        }
        if (now == null) {
            throw new IllegalArgumentException("now must not be null");
        }

        removeExpired(now);
        if (controlsByKey.containsKey(idempotencyKey)) {
            return ActivationResult.IDEMPOTENT_REPLAY;
        }
        if (!expiresAt.isAfter(now)) {
            return ActivationResult.EXPIRED;
        }
        if (controlsByKey.size() >= capacity) {
            return ActivationResult.CAPACITY_REJECTED;
        }

        controlsByKey.put(idempotencyKey, new ActiveControl(canonicalIp, action, expiresAt));
        Set<String> keys = controlKeysByIp.get(canonicalIp);
        if (keys == null) {
            keys = new HashSet<String>();
            controlKeysByIp.put(canonicalIp, keys);
        }
        keys.add(idempotencyKey);
        return ActivationResult.ACTIVATED;
    }

    @Override
    public synchronized IpControlDecision check(String canonicalIp, Instant now) {
        requireText(canonicalIp, "canonicalIp");
        if (now == null) {
            throw new IllegalArgumentException("now must not be null");
        }

        removeExpiredForIp(canonicalIp, now);
        boolean rateLimited = false;
        Set<String> keys = controlKeysByIp.get(canonicalIp);
        if (keys == null) {
            return IpControlDecision.allowed();
        }
        for (String key : keys) {
            ActiveControl control = controlsByKey.get(key);
            if (control.action == ControlActionType.DENY) {
                return IpControlDecision.denied();
            }
            if (control.action == ControlActionType.RATE_LIMIT) {
                rateLimited = true;
            }
        }
        if (!rateLimited) {
            rateWindowsByIp.remove(canonicalIp);
            return IpControlDecision.allowed();
        }

        RateWindow window = rateWindowsByIp.get(canonicalIp);
        if (window == null || !now.isBefore(window.startedAt.plus(fixedWindow))) {
            rateWindowsByIp.put(canonicalIp, new RateWindow(now, 1));
            return IpControlDecision.allowed();
        }
        if (window.permitsUsed < permitsPerWindow) {
            window.permitsUsed++;
            return IpControlDecision.allowed();
        }
        return IpControlDecision.rateLimited(Duration.between(now, window.startedAt.plus(fixedWindow)));
    }

    private void removeExpired(Instant now) {
        Iterator<Map.Entry<String, ActiveControl>> iterator = controlsByKey.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, ActiveControl> entry = iterator.next();
            if (!entry.getValue().expiresAt.isAfter(now)) {
                iterator.remove();
                removeIndexedKey(entry.getKey(), entry.getValue().canonicalIp);
            }
        }
        rateWindowsByIp.keySet().retainAll(controlKeysByIp.keySet());
    }

    private void removeExpiredForIp(String canonicalIp, Instant now) {
        Set<String> keys = controlKeysByIp.get(canonicalIp);
        if (keys == null) {
            rateWindowsByIp.remove(canonicalIp);
            return;
        }
        Iterator<String> iterator = keys.iterator();
        while (iterator.hasNext()) {
            String key = iterator.next();
            ActiveControl control = controlsByKey.get(key);
            if (control == null || !control.expiresAt.isAfter(now)) {
                iterator.remove();
                controlsByKey.remove(key);
            }
        }
        if (keys.isEmpty()) {
            controlKeysByIp.remove(canonicalIp);
            rateWindowsByIp.remove(canonicalIp);
        }
    }

    private void removeIndexedKey(String key, String canonicalIp) {
        Set<String> keys = controlKeysByIp.get(canonicalIp);
        if (keys == null) {
            return;
        }
        keys.remove(key);
        if (keys.isEmpty()) {
            controlKeysByIp.remove(canonicalIp);
            rateWindowsByIp.remove(canonicalIp);
        }
    }

    private static void requireSupportedAction(ControlActionType action) {
        if (action != ControlActionType.DENY && action != ControlActionType.RATE_LIMIT) {
            throw new IllegalArgumentException("action must be DENY or RATE_LIMIT");
        }
    }

    private static void requireText(String value, String name) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }

    private static final class ActiveControl {
        private final String canonicalIp;
        private final ControlActionType action;
        private final Instant expiresAt;

        private ActiveControl(String canonicalIp, ControlActionType action, Instant expiresAt) {
            this.canonicalIp = canonicalIp;
            this.action = action;
            this.expiresAt = expiresAt;
        }
    }

    private static final class RateWindow {
        private final Instant startedAt;
        private int permitsUsed;

        private RateWindow(Instant startedAt, int permitsUsed) {
            this.startedAt = startedAt;
            this.permitsUsed = permitsUsed;
        }
    }
}

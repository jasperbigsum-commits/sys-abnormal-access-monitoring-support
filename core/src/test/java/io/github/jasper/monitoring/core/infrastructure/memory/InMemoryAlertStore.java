package io.github.jasper.monitoring.core.infrastructure.memory;

import io.github.jasper.monitoring.core.domain.AlertDisposition;
import io.github.jasper.monitoring.core.domain.SecurityAlert;
import io.github.jasper.monitoring.core.port.AlertRepository;
import io.github.jasper.monitoring.core.port.MonitoringTransaction;
import io.github.jasper.monitoring.core.port.TransactionWork;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Alert-only transactional test fake. */
public final class InMemoryAlertStore implements AlertRepository, MonitoringTransaction {
    private final Map<String, SecurityAlert> alerts = new LinkedHashMap<String, SecurityAlert>();
    private final Map<String, List<AlertDisposition>> dispositions =
        new LinkedHashMap<String, List<AlertDisposition>>();

    @Override public synchronized <T> T required(TransactionWork<T> work) {
        Map<String, SecurityAlert> previousAlerts = new LinkedHashMap<String, SecurityAlert>(alerts);
        Map<String, List<AlertDisposition>> previousDispositions = copy(dispositions);
        try { return work.execute(); }
        catch (RuntimeException exception) {
            alerts.clear(); alerts.putAll(previousAlerts);
            dispositions.clear(); dispositions.putAll(previousDispositions);
            throw exception;
        }
    }
    @Override public synchronized void save(SecurityAlert alert) { alerts.put(alert.getAlertId(), alert); }
    @Override public synchronized Optional<SecurityAlert> findAlert(String alertId) { return Optional.ofNullable(alerts.get(alertId)); }
    @Override public synchronized Optional<SecurityAlert> findOpen(String fingerprint) { for (SecurityAlert alert : alerts.values()) if (alert.getFingerprint().equals(fingerprint) && alert.getStatus().isOpen()) return Optional.of(alert); return Optional.empty(); }
    @Override public void linkEvent(String alertId, String eventId) { }
    @Override public synchronized void appendDisposition(AlertDisposition disposition) { dispositions.computeIfAbsent(disposition.getAlertId(), key -> new ArrayList<AlertDisposition>()).add(disposition); }
    @Override public synchronized List<AlertDisposition> findDispositions(String alertId) { List<AlertDisposition> result = dispositions.get(alertId); return result == null ? Collections.<AlertDisposition>emptyList() : Collections.unmodifiableList(new ArrayList<AlertDisposition>(result)); }
    public synchronized List<SecurityAlert> getAlerts() { return Collections.unmodifiableList(new ArrayList<SecurityAlert>(alerts.values())); }

    private static Map<String, List<AlertDisposition>> copy(Map<String, List<AlertDisposition>> source) {
        Map<String, List<AlertDisposition>> result = new LinkedHashMap<String, List<AlertDisposition>>();
        for (Map.Entry<String, List<AlertDisposition>> entry : source.entrySet()) result.put(entry.getKey(), new ArrayList<AlertDisposition>(entry.getValue()));
        return result;
    }
}

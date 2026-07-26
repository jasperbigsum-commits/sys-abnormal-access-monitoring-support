package io.github.jasper.monitoring.core.port;

/** Explicit transaction boundary for monitoring aggregate writes. */
public interface MonitoringTransaction {
    <T> T required(TransactionWork<T> work);
}

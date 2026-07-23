package io.github.jasper.monitoring.core.port;

/**
 * Work that must run within one monitoring persistence transaction.
 *
 * <p>Implementations may join an existing transaction. Callbacks must only perform monitoring
 * persistence work: notification delivery and host control actions belong after commit because
 * neither can be rolled back reliably.</p>
 *
 * @param <T> result returned after a successful commit
 */
@FunctionalInterface
public interface TransactionWork<T> {
    /**
     * Executes the persistence work.
     *
     * @return transaction result
     */
    T execute();
}

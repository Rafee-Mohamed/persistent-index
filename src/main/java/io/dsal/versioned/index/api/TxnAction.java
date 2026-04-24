package io.dsal.versioned.index.api;

/**
 * Transaction callback without a return value.
 *
 * @param <K> key type
 * @param <V> value type
 * @param <E> checked exception type that may be thrown by the action
 */
@FunctionalInterface
public interface TxnAction<K, V, E extends Exception> {
    /**
     * Executes this action against the given transaction handle.
     *
     * @param th transaction handle
     * @throws E if the action fails
     */
    void apply(TxnHandle<K, V> th) throws E;
}

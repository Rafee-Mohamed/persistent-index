package io.dsal.versioned.index.api;

/**
 * Transaction callback that returns a result.
 *
 * @param <K> key type
 * @param <V> value type
 * @param <R> callback result type
 * @param <E> checked exception type that may be thrown by the callback
 */
@FunctionalInterface
public interface TxnBlock<K, V, R, E extends Exception> {
    /**
     * Executes this block against the given transaction handle.
     *
     * @param th transaction handle
     * @return callback result
     * @throws E if execution fails
     */
    R apply(TxnHandle<K, V> th) throws E;
}

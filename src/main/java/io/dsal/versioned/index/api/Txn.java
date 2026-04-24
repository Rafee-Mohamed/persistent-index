package io.dsal.versioned.index.api;

/**
 * Transaction context with explicit lifecycle and atomic commit.
 *
 * <p>A transaction accumulates mutations in a private working state. Mutations
 * become externally visible only when {@link #commit()} succeeds.
 *
 * <p>Commit semantics:
 * <ul>
 *   <li>all transaction mutations become visible as one atomic state transition</li>
 *   <li>no transaction intermediate state is externally visible</li>
 * </ul>
 *
 * <p>After commit, the transaction handle is invalid for further use.
 *
 * @param <K> key type
 * @param <V> value type
 */
public interface Txn<K, V> extends TxnHandle<K, V> {
    /**
     * Returns whether this transaction has already been committed.
     *
     * <p>This method is informational and is not intended as primary control
     * flow for normal usage. Implementations may still throw
     * {@link IllegalStateException} when operations are invoked after commit.
     *
     * @return {@code true} if committed, otherwise {@code false}
     */
    boolean committed();

    /**
     * Commits all mutations currently accumulated in this transaction.
     *
     * <p>After a successful commit, this transaction handle is no longer valid
     * for further read or write operations.
     *
     * @throws IllegalStateException if the transaction has already been committed
     */
    void commit();
}

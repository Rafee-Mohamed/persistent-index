package io.dsal.versioned.index.api;

/**
 * Transaction-scoped mutable view that supports both reads and writes.
 *
 * <p>Reads through the handle observe the transaction's current working state,
 * including mutations already applied through the same handle.
 *
 * <p>The committed base state captured at transaction start is available via
 * {@link #snapshot()} and remains stable for the transaction lifetime.
 *
 * <p>Thread-safety: transaction handles are mutable and are expected to be
 * confined to one thread unless an implementation explicitly documents stronger
 * guarantees.
 *
 * <p>Iteration while mutating through the same transaction handle is not
 * guaranteed unless implementation documentation states otherwise. Use
 * {@link #snapshot()} for stable iteration semantics.
 *
 * @param <K> key type
 * @param <V> value type
 */
public interface TxnHandle<K, V> extends ReadView<K, V>, Mutator<K, V> {
    /**
     * Returns the committed base snapshot captured when this transaction started.
     *
     * <p>The returned snapshot:
     * <ul>
     *   <li>does not include transaction-local uncommitted mutations</li>
     *   <li>does not change for the lifetime of the transaction</li>
     * </ul>
     *
     * @return committed base snapshot for this transaction
     * @throws IllegalStateException if this handle can no longer be used
     */
    Snapshot<K, V> snapshot();
}

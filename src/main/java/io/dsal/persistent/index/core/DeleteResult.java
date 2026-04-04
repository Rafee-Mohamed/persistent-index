package io.dsal.persistent.index.core;

/**
 * Outcome of {@link PersistentBPlusTree}'s recursive delete: key missing, delete with
 * no structural shrink at this level, or delete where a child underflowed
 * ({@code Shrink}) so an ancestor may need borrow or merge.
 *
 * <pre>
 *   NotFound   --&gt;  key absent; {@link #removed()} is {@code null}
 *   NoShrink   --&gt;  subtree updated; height unchanged at this level
 *   Shrink     --&gt;  child too empty; parent may rebalance or merge upward
 * </pre>
 *
 * @param <K> key type
 * @param <V> value type
 * @see PersistentBPlusTree#remove(Node, K)
 */
public sealed interface DeleteResult<K, V> {
    /**
     * Value removed for the key, or {@code null} if nothing was deleted
     * ({@link NotFound}).
     */
    default V removed() {
        return null;
    }

    /** Key was not present in the subtree. */
    record NotFound<K, V>() implements DeleteResult<K, V> {}

    /**
     * Delete applied; this node or a descendant was rewritten without requiring a
     * shrink repair at the parent (child stayed at or above minimum fill, or borrow
     * fixed underflow at this level).
     *
     * @param node    new root of the updated subtree
     * @param removed value that was stored for the deleted key
     */
    record NoShrink<K, V>(
            Node<K, V> node,
            V removed
    ) implements DeleteResult<K, V> {}

    /**
     * Delete applied but a child violated minimum fill after the removal; the
     * returned {@code node} is the current subtree root for the parent to repair
     * (borrow, merge, or hoist). May chain up until {@link PersistentBPlusTree}
     * absorbs the shrink at the root.
     *
     * @param node    subtree root after delete (may underflow)
     * @param removed value removed for the key
     */
    record Shrink<K, V>(
            Node<K, V> node,
            V removed
    ) implements DeleteResult<K, V> {}
}

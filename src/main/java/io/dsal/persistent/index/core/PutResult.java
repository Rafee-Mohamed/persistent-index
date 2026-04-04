package io.dsal.persistent.index.core;

/**
 * Outcome of {@link PersistentBPlusTree}'s recursive insert: either the subtree
 * shape is unchanged, or a child split produced two subtrees plus a promoted key.
 *
 * <p>{@link #replaced()} defaults to {@code null} (like {@link DeleteResult#removed()}).
 * {@link NoSplit} overrides it with the prior value for the key when present.</p>
 *
 * <pre>
 *   NoSplit   --&gt;  one updated {@link Node} (replace or in-place growth)
 *   Split     --&gt;  left + right {@link Node}s + {@code promotedKey} for the parent
 * </pre>
 *
 * <p>See {@link PersistentBPlusTree#put(Node, Object, Object) PersistentBPlusTree.put(Node&lt;K,V&gt;, K, V)}.</p>
 *
 * @param <K> key type
 * @param <V> value type
 */
public sealed interface PutResult<K, V> {

    /**
     * Prior value for this key ({@link java.util.Map#put(Object, Object)} semantics), or
     * {@code null} if absent or if this result is {@link Split} (default implementation).
     */
    default V replaced() {
        return null;
    }

    /**
     * Insert or update without splitting this subtree root.
     *
     * @param node     new root of the updated subtree
     * @param replaced prior value for the key, or {@code null} if newly inserted
     */
    record NoSplit<K, V>(
            Node<K, V> node,
            V replaced
    ) implements PutResult<K, V> {}

    /**
     * Child (or leaf) split: two new subtree roots and a boundary key for the parent.
     * {@link #replaced()} stays the default {@code null}.
     *
     * <pre>
     *   promotedKey  = smallest key in the right partition (same as {@link io.dsal.persistent.index.layout.KeySplit})
     *   left | right = new {@link Node.Internal} or {@link Node.Leaf} pair
     * </pre>
     *
     * @param left         lower partition after split
     * @param right        upper partition after split
     * @param promotedKey  separator for the parent (from the key split)
     */
    record Split<K, V>(
            Node<K, V> left,
            Node<K, V> right,
            K promotedKey
    ) implements PutResult<K, V> {}
}

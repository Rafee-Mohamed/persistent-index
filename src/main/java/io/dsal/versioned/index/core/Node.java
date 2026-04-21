package io.dsal.versioned.index.core;

import io.dsal.versioned.index.layout.KeyStorage;
import io.dsal.versioned.index.layout.ValueStorage;

/**
 * Discriminated union of B+ tree nodes: internal (routing) vs leaf (payload).
 * Structural invariants are enforced by {@link PersistentBPlusTree}; storage
 * primitives are {@link KeyStorage}, {@link Children}, and {@link ValueStorage}.
 *
 * <pre>
 *   Internal:   keys [ k0 | k1 | ... ] children  c0  c1  c2  ...   ({@code children.size() == keys.size() + 1})
 *   Leaf:       keys [ ... ]           values    (same length as keys)
 * </pre>
 *
 * @param <K> key type
 * @param <V> value type
 * @see PersistentBPlusTree
 */
public sealed interface Node<K, V> {

    /** Separator or leaf keys for this node (never {@code null}). */
    KeyStorage<K> keys();

    /**
     * Branch node: ordered separator keys and one more child pointer than keys.
     *
     * @param keys     internal separators
     * @param children child subtrees, aligned with {@link Children}
     */
    record Internal<K, V>(
            KeyStorage<K> keys,
            Children<K, V> children
    ) implements Node<K, V> {
    }

    /**
     * Leaf node: parallel key and value columns ({@code keys.size() == values.size()}).
     *
     * @param keys   sorted leaf keys
     * @param values values at the same indices as {@code keys}
     */
    record Leaf<K, V>(
            KeyStorage<K> keys,
            ValueStorage<V> values
    ) implements Node<K, V> {
    }
}

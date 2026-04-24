package io.dsal.versioned.index.persistent.core;

/**
 * Immutable committed tree state: root pointer plus logical size.
 *
 * <p>Instances are published by {@link StateCommitter} and referenced by
 * snapshots and newly created transactions.
 *
 * @param root committed root node, or {@code null} for empty tree
 * @param size number of committed key-value pairs
 * @param <K> key type
 * @param <V> value type
 */
public record CommittedState<K, V>(Node<K, V> root, int size) {
    /**
     * Creates an empty committed state.
     */
    CommittedState() {
        this(null, 0);
    }
}

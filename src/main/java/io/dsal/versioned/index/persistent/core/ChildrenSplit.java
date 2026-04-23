package io.dsal.versioned.index.persistent.core;

import io.dsal.versioned.index.core.PersistentBPlusTree;

/**
 * Outcome of partitioning an internal node's child pointers when that node would
 * exceed the maximum key count: the {@link Children} counterpart to
 * {@link io.dsal.versioned.index.layout.KeySplit}. A child that split into two
 * nodes is reflected here by dividing the pointer array so it still matches the
 * parent's keys ({@code children.size() == keys.size() + 1} for {@link Node.Internal}).
 *
 * <p>{@link #left()} and {@link #right()} are produced with the same indices as
 * {@link Children#insertAndSplit(int, int, Node, Node)} alongside a key split; see
 * {@link PersistentBPlusTree}.</p>
 *
 * @param left  child pointers for the left partition
 * @param right child pointers for the right partition
 * @param <K>   key type
 * @param <V>   value type
 */
public record ChildrenSplit<K, V>(
        Children<K, V> left,
        Children<K, V> right
) {
}

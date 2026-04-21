package io.dsal.versioned.index.iterator;

import io.dsal.versioned.index.core.Node;

/**
 * One stack level while walking the tree: an internal node plus which child index to
 * take next. Used by {@link BTreeIterator} and {@link BoundedBTreeIterator} to resume
 * in-order traversal after finishing a leaf.
 */
class Frame<K, V> {
    /** Branch node at this depth on the path from the root. */
    final Node.Internal<K, V> node;

    /** Next child slot to return from {@link #next()}; advances on each call. */
    int idx;

    Frame(Node.Internal<K, V> node) {
        this.node = node;
        this.idx = 0;
    }

    Frame(Node.Internal<K, V> node, int idx) {
        this.node = node;
        this.idx = idx;
    }

    /** Returns the child at {@code idx} and increments, or {@code null} if exhausted. */
    Node<K, V> next() {
        if (idx >= node.children().size()) {
            return null;
        }

        return node.children().child(idx++);
    }
}

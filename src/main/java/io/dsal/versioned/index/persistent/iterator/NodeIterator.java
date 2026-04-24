package io.dsal.versioned.index.persistent.iterator;

import io.dsal.versioned.index.persistent.core.Node;

import java.util.Iterator;
import java.util.NoSuchElementException;


/**
 * Forward iterator over the child pointers of a {@link Node.Internal}, from a
 * given start index to the last child.
 *
 * @param <K> key type
 * @param <V> value type
 */
class NodeIterator<K, V> implements Iterator<Node<K, V>> {

    private final Node.Internal<K, V> node;
    private int idx;

    /**
     * Creates a forward iterator starting at child index {@code start}.
     *
     * @param node  internal node whose children to iterate
     * @param start first child index (inclusive)
     */
    NodeIterator(Node.Internal<K, V> node, int start) {
        this.node = node;
        this.idx = start;
    }

    /**
     * Creates a forward iterator starting at the first child (index 0).
     *
     * @param node internal node whose children to iterate
     */
    NodeIterator(Node.Internal<K, V> node) {
        this(node, 0);
    }

    @Override
    public boolean hasNext() {
        return idx < node.children().size();
    }

    @Override
    public Node<K, V> next() {
        if (!hasNext()) {
            throw new NoSuchElementException();
        }
        return node.children().child(idx++);
    }
}

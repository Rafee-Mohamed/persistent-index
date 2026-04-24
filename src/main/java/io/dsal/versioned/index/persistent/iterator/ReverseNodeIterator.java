package io.dsal.versioned.index.persistent.iterator;

import io.dsal.versioned.index.persistent.core.Node;

import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * Reverse iterator over the child pointers of a {@link Node.Internal}, from a
 * given end index down to child 0.
 *
 * @param <K> key type
 * @param <V> value type
 */
public class ReverseNodeIterator<K, V>  implements Iterator<Node<K, V>> {
    private final Node.Internal<K, V> node;
    private int idx;

    /**
     * Creates a reverse iterator starting at child index {@code end}.
     *
     * @param node internal node whose children to iterate in reverse
     * @param end  first child index to yield (highest; iteration goes downward)
     */
    ReverseNodeIterator(Node.Internal<K, V> node, int end) {
        this.node = node;
        this.idx = end;
    }

    /**
     * Creates a reverse iterator starting at the last child.
     *
     * @param node internal node whose children to iterate in reverse
     */
    ReverseNodeIterator(Node.Internal<K, V> node) {
        this(node, node.children().size() - 1);
    }

    @Override
    public boolean hasNext() {
        return idx >= 0;
    }


    @Override
    public Node<K, V> next() {
        if (!hasNext()) {
            throw new NoSuchElementException();
        }

        return node.children().child(idx--);
    }
}

package io.dsal.versioned.index.persistent.iterator;

import io.dsal.versioned.index.persistent.core.Node;

import java.util.Iterator;
import java.util.NoSuchElementException;

public class ReverseNodeIterator<K, V>  implements Iterator<Node<K, V>> {
    private final Node.Internal<K, V> node;
    private int idx;

    ReverseNodeIterator(Node.Internal<K, V> node, int end) {
        this.node = node;
        this.idx = end;
    }

    ReverseNodeIterator(Node.Internal<K, V> node) {
        this(node, 0);
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

package io.dsal.versioned.index.persistent.iterator;

import io.dsal.versioned.index.persistent.core.Node;

import java.util.Iterator;
import java.util.NoSuchElementException;


class NodeIterator<K, V> implements Iterator<Node<K, V>> {

    private final Node.Internal<K, V> node;
    private int idx;

    NodeIterator(Node.Internal<K, V> node, int start) {
        this.node = node;
        this.idx = start;
    }

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

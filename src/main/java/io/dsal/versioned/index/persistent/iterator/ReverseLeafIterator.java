package io.dsal.versioned.index.persistent.iterator;

import io.dsal.versioned.index.api.Entry;
import io.dsal.versioned.index.persistent.core.Node;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.function.BiFunction;

public class ReverseLeafIterator<K, V, E> implements Iterator<E> {

    private final Node.Leaf<K, V> leaf;
    private int idx;
    private final int start;
    private final BiFunction<K, V, E> mapper;

    public ReverseLeafIterator(Node.Leaf<K, V> leaf, BiFunction<K, V, E> mapper, int start, int end) {
        this.leaf = leaf;
        this.start = start;
        this.idx = end;
        this.mapper = mapper;
    }

    public ReverseLeafIterator(Node.Leaf<K, V> leaf, BiFunction<K, V, E> mapper, int start) {
        this(leaf, mapper, start, leaf.keys().size() - 1);
    }

    public ReverseLeafIterator(Node.Leaf<K, V> leaf, BiFunction<K, V, E> mapper) {
        this(leaf, mapper, 0, leaf.keys().size() - 1);
    }


    @Override
    public boolean hasNext() {
        return idx >= start;
    }

    @Override
    public E next() {
        if (!hasNext()) {
            throw new NoSuchElementException();
        }
        var next = idx--;
        return mapper.apply(leaf.keys().key(next), leaf.values().val(next));
    }
}


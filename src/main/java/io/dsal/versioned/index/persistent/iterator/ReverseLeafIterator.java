package io.dsal.versioned.index.persistent.iterator;

import io.dsal.versioned.index.api.Entry;
import io.dsal.versioned.index.persistent.core.Node;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.function.BiFunction;

/**
 * Reverse (descending) iterator over a contiguous slice of a {@link Node.Leaf},
 * from index {@code end} down to {@code start} inclusive.
 *
 * @param <K> key type
 * @param <V> value type
 * @param <E> element type produced by the mapper
 */
public class ReverseLeafIterator<K, V, E> implements Iterator<E> {

    private final Node.Leaf<K, V> leaf;
    private int idx;
    private final int start;
    private final BiFunction<K, V, E> mapper;

    /**
     * Creates a reverse iterator over {@code leaf} entries in {@code [start, end]}
     * inclusive, yielding them from {@code end} down to {@code start}.
     *
     * @param leaf   leaf node to iterate
     * @param mapper function applied to each key-value pair
     * @param start  lower bound index (inclusive)
     * @param end    upper bound index (inclusive); iteration begins here
     */
    public ReverseLeafIterator(Node.Leaf<K, V> leaf, BiFunction<K, V, E> mapper, int start, int end) {
        this.leaf = leaf;
        this.start = start;
        this.idx = end;
        this.mapper = mapper;
    }

    /**
     * Creates a reverse iterator over {@code leaf} entries from the last index down
     * to {@code start} inclusive.
     *
     * @param leaf   leaf node to iterate
     * @param mapper function applied to each key-value pair
     * @param start  lower bound index (inclusive)
     */
    public ReverseLeafIterator(Node.Leaf<K, V> leaf, BiFunction<K, V, E> mapper, int start) {
        this(leaf, mapper, start, leaf.keys().size() - 1);
    }

    /**
     * Creates a reverse iterator over all entries in {@code leaf}.
     *
     * @param leaf   leaf node to iterate
     * @param mapper function applied to each key-value pair
     */
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


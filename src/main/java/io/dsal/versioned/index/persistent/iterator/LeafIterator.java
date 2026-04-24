package io.dsal.versioned.index.persistent.iterator;

import io.dsal.versioned.index.persistent.core.Node;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.function.BiFunction;

/**
 * Forward iterator over a contiguous slice of a {@link Node.Leaf}, from index
 * {@code start} to {@code end} inclusive.
 *
 * @param <K> key type
 * @param <V> value type
 * @param <E> element type produced by the mapper
 */
public class LeafIterator<K, V, E> implements Iterator<E> {

    private final Node.Leaf<K, V> leaf;
    private int idx;
    private final int end;
    private final BiFunction<K, V, E> mapper;

    /**
     * Creates an iterator over {@code leaf} entries in {@code [start, end]} inclusive.
     *
     * @param leaf   leaf node to iterate
     * @param mapper function applied to each key-value pair
     * @param start  first index (inclusive)
     * @param end    last index (inclusive)
     */
    public LeafIterator(Node.Leaf<K, V> leaf, BiFunction<K, V, E> mapper, int start, int end) {
        this.leaf = leaf;
        this.idx = start;
        this.end = end;
        this.mapper = mapper;
    }

    /**
     * Creates an iterator over {@code leaf} entries in {@code [0, end]} inclusive.
     *
     * @param leaf   leaf node to iterate
     * @param mapper function applied to each key-value pair
     * @param end    last index (inclusive)
     */
    public LeafIterator(Node.Leaf<K, V> leaf, BiFunction<K, V, E> mapper, int end) {
        this(leaf, mapper, 0, end);
    }

    /**
     * Creates an iterator over all entries in {@code leaf}.
     *
     * @param leaf   leaf node to iterate
     * @param mapper function applied to each key-value pair
     */
    public LeafIterator(Node.Leaf<K, V> leaf, BiFunction<K, V, E> mapper) {
        this(leaf, mapper, 0, leaf.keys().size() - 1);
    }


    @Override
    public boolean hasNext() {
        return idx <= end;
    }

    @Override
    public E next() {
        if (!hasNext()) {
            throw new NoSuchElementException();
        }
        var next = idx++;
        return mapper.apply(leaf.keys().key(next), leaf.values().val(next));
    }
}

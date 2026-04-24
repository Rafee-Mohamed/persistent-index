package io.dsal.versioned.index.persistent.iterator;

import io.dsal.versioned.index.api.Direction;
import io.dsal.versioned.index.persistent.core.Node;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.function.BiFunction;
import java.util.function.Function;


/**
 * Lazy iterator over all entries in a B+ tree in ascending or descending key order.
 *
 * <p>The iterator descends to the first leaf at construction time and maintains an
 * ancestor path stack so that {@link #hasNext()} can advance to the next sibling
 * leaf without re-reading the tree root. Total work is O(n) with O(log n) stack
 * space. The tree must not be structurally modified while the iterator is in use.
 *
 * @param <K> key type
 * @param <V> value type
 * @param <E> element type produced by the mapper
 */
public class BTreeIterator<K, V, E> implements Iterator<E> {

    /** Ancestor path stack; each entry iterates the children of one internal node. */
    private final List<Iterator<Node<K, V>>> path;

    /** Produces a child iterator for an internal node in traversal direction order. */
    private final Function<Node.Internal<K, V>, Iterator<Node<K, V>>> nodePathMapper;

    /** Produces an entry iterator for a leaf node in traversal direction order. */
    private final Function<Node.Leaf<K, V>, Iterator<E>>  leafIteratorMapper;

    /** Active leaf entry iterator; exhausted when the current leaf is done. */
    private Iterator<E> leafEntries;

    private BTreeIterator(
            List<Iterator<Node<K, V>>> path,
            Function<Node.Internal<K, V>, Iterator<Node<K, V>>> nodePathMapper,
            Function<Node.Leaf<K, V>, Iterator<E>> leafIteratorMapper,
            Iterator<E> leafEntries
    ) {
        this.path = path;
        this.nodePathMapper = nodePathMapper;
        this.leafIteratorMapper = leafIteratorMapper;
        this.leafEntries = leafEntries;
    }


    /**
     * Creates an iterator starting at the first (or last for DESC) leaf entry.
     *
     * @param node      tree root; must not be {@code null}
     * @param direction traversal direction
     * @param mapper    function applied to each key-value pair to produce an element
     * @param <K>       key type
     * @param <V>       value type
     * @param <E>       element type
     * @return iterator positioned at the first element in direction order
     */
    public static <K, V, E> BTreeIterator<K, V, E> of(Node<K, V> node, Direction direction,  BiFunction<K, V, E> mapper) {

        Function<Node.Internal<K, V>, Iterator<Node<K, V>>> nodePathMapper = switch (direction) {
            case Direction.ASC -> NodeIterator::new;
            case Direction.DESC -> ReverseNodeIterator::new;
        };

        Function<Node.Leaf<K, V>, Iterator<E>>  leafIteratorMapper = switch (direction) {
            case Direction.ASC -> leaf ->  new LeafIterator<>(leaf, mapper);
            case Direction.DESC ->  leaf -> new ReverseLeafIterator<>(leaf, mapper);
        };

        var path = new ArrayList<Iterator<Node<K, V>>>();
        while (node instanceof Node.Internal<K,V> next) {
            var it = nodePathMapper.apply(next);
            path.add(it);
            node = it.next();
        }

        assert node instanceof Node.Leaf<K, V>;
        var leaf = (Node.Leaf<K, V>) node;

        return new BTreeIterator<>(path, nodePathMapper, leafIteratorMapper, leafIteratorMapper.apply(leaf));
    }


    @Override
    public boolean hasNext() {
        if (leafEntries.hasNext()) {
            return true;
        }

        if (path.isEmpty()) {
            return false;
        }

        while (!path.isEmpty() && !path.getLast().hasNext()) {
            path.removeLast();
        }

        if (path.isEmpty()) {
            return false;
        }

        var node = path.getLast().next();
        while (node instanceof Node.Internal<K,V> nextNode) {
            var it = nodePathMapper.apply(nextNode);
            path.add(it);
            node = it.next();
        }

        assert node instanceof Node.Leaf<K, V>;
        leafEntries = leafIteratorMapper.apply((Node.Leaf<K, V>) node);

        return true;
    }

    @Override
    public E next() {
        if (!hasNext()) {
            throw new NoSuchElementException();
        }
        return leafEntries.next();
    }
}

package io.dsal.versioned.index.persistent.iterator;

import io.dsal.versioned.index.api.Direction;
import io.dsal.versioned.index.api.Range;
import io.dsal.versioned.index.api.RangeType;
import io.dsal.versioned.index.persistent.core.Node;
import io.dsal.versioned.index.persistent.util.Search;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Lazy iterator over entries in a {@link io.dsal.versioned.index.api.Range} of a
 * B+ tree, in ascending or descending key order.
 *
 * <p>Descent uses range-aware child selection at each internal node so only subtrees
 * that can contain keys within the range are visited. Leaves outside the range
 * produce empty optional iterators and are skipped in {@link #hasNext()}. The tree
 * must not be structurally modified while the iterator is in use.
 *
 * @param <K> key type
 * @param <V> value type
 * @param <E> element type produced by the mapper
 */
public class BoundedBTreeIterator<K, V, E> implements Iterator<E> {

    /** Ancestor path stack; each entry iterates an internal node's children. */
    private final List<Iterator<Node<K, V>>> path;

    /** Full-range child iterator for interior nodes (used after the initial descent). */
    private final Function<Node.Internal<K, V>, Iterator<Node<K, V>>> nodePathMapper;

    /**
     * Produces a range-bounded leaf entry iterator; returns {@link Optional#empty()}
     * when the leaf falls entirely outside the range.
     */
    private final Function<Node.Leaf<K, V>, Optional<Iterator<E>>>  leafIteratorMapper;

    /** Active leaf entry iterator; exhausted when the current leaf is done. */
    private Iterator<E> leafEntries;

    private BoundedBTreeIterator(
            List<Iterator<Node<K, V>>> path,
            Function<Node.Internal<K, V>, Iterator<Node<K, V>>> nodePathMapper,
            Function<Node.Leaf<K, V>, Optional<Iterator<E>>>  leafIteratorMapper,
            Iterator<E> leafEntries
    ) {
        this.path = path;
        this.nodePathMapper = nodePathMapper;
        this.leafIteratorMapper = leafIteratorMapper;
        this.leafEntries = leafEntries;
    }

    /**
     * Creates a range-bounded iterator positioned at the first entry in {@code range}
     * and {@code direction} order.
     *
     * @param node      tree root; must not be {@code null}
     * @param direction traversal direction
     * @param range     range bounds and endpoint policy
     * @param mapper    function applied to each key-value pair
     * @param <K>       key type
     * @param <V>       value type
     * @param <E>       element type
     * @return iterator over entries within the range in direction order
     */
    public static <K, V, E> BoundedBTreeIterator<K, V, E> of(Node<K, V> node, Direction direction, Range<K> range, BiFunction<K, V, E> mapper) {
        var nodeBoundedPathMapper = BoundedBTreeIterator.<K, V>nodeBoundedPathMapper(direction, range);
        var leafIteratorMapper = leafIteratorMapper(direction, range, mapper);

        Function<Node.Internal<K, V>, Iterator<Node<K, V>>> nodePathMapper = switch (direction) {
            case Direction.ASC -> NodeIterator::new;
            case Direction.DESC -> ReverseNodeIterator::new;
        };

        var path = new ArrayList<Iterator<Node<K, V>>>();
        while (node instanceof Node.Internal<K,V> next) {
            var it = nodeBoundedPathMapper.apply(next);
            path.add(it);
            node = it.next();
        }

        assert node instanceof Node.Leaf<K, V>;
        var leaf = (Node.Leaf<K, V>) node;

        var start = 0;
        var end = 0;

        switch (range.type()) {
            case RangeType.OPEN -> {
                start = Search.upperBound(leaf.keys(), range.from());
                end = Search.predecessor(leaf.keys(), range.to());
            }
            case RangeType.OPEN_CLOSED -> {
                start = Search.upperBound(leaf.keys(), range.from());
                end = Search.floor(leaf.keys(), range.to());
            }
            case RangeType.CLOSED -> {
                start = Search.lowerBound(leaf.keys(), range.from());
                end = Search.floor(leaf.keys(), range.to());
            }
            case RangeType.CLOSED_OPEN -> {
                start = Search.lowerBound(leaf.keys(), range.from());
                end = Search.predecessor(leaf.keys(), range.to());
            }
        };

        Iterator<E> leafEntries = switch (direction) {
            case Direction.ASC -> new LeafIterator<>(leaf, mapper, start, end);
            case Direction.DESC -> new ReverseLeafIterator<>(leaf, mapper, start, end);
        };

        return new BoundedBTreeIterator<>(path, nodePathMapper, leafIteratorMapper, leafEntries);
    }

    /**
     * Produces a range-aware child iterator for the initial descent into internal
     * nodes: starts at the child covering the range start, ends at the child
     * covering the range end, based on {@code direction} and bound type.
     *
     * @param direction traversal direction
     * @param range     range to constrain the initial descent
     * @param <K>       key type
     * @param <V>       value type
     * @return function mapping an internal node to a bounded child iterator
     */
    private static <K, V> Function<Node.Internal<K, V>, Iterator<Node<K, V>>> nodeBoundedPathMapper(Direction direction, Range<K> range) {
        return switch (direction) {
            case Direction.ASC ->
                    nextNode -> new NodeIterator<>(nextNode, Search.lowerBound(nextNode.keys(), range.from()));
            case Direction.DESC ->
                    nextNode -> new ReverseNodeIterator<>(nextNode, Search.upperBound(nextNode.keys(), range.to()));
        };
    }

    /**
     * Produces a factory that yields a range-bounded leaf entry iterator, or
     * {@link Optional#empty()} when the leaf contains no entries within the range.
     * The start/end index within each leaf is computed from the range bounds and
     * bound type using {@link io.dsal.versioned.index.persistent.util.Search} operations.
     *
     * @param direction traversal direction
     * @param range     range bounds
     * @param mapper    entry mapper
     * @param <K>       key type
     * @param <V>       value type
     * @param <E>       element type
     * @return function mapping a leaf to an optional bounded entry iterator
     */
    private static <K, V, E> Function<Node.Leaf<K, V>, Optional<Iterator<E>>>  leafIteratorMapper(Direction direction, Range<K> range, BiFunction<K, V, E> mapper) {
        return switch (direction) {
            case Direction.ASC -> switch (range.type()) {
                case RangeType.OPEN ->
                        nextNode -> {
                            var start = Search.upperBound(nextNode.keys(), range.from());
                            var end = Search.predecessor(nextNode.keys(), range.to());
                            if (end < 0) {
                                return Optional.empty();
                            }
                            return Optional.of(new LeafIterator<>(nextNode, mapper, start, end));
                        };
                case RangeType.CLOSED_OPEN ->
                        nextNode -> {
                            var end = Search.predecessor(nextNode.keys(), range.to());
                            if (end < 0) {
                                return Optional.empty();
                            }
                            return Optional.of(new LeafIterator<>(nextNode, mapper, end));
                        };
                case RangeType.CLOSED ->
                        nextNode -> {
                            var end = Search.floor(nextNode.keys(), range.to());
                            if (end < 0) {
                                return Optional.empty();
                            }
                            return Optional.of(new LeafIterator<>(nextNode, mapper, end));
                        };
                case RangeType.OPEN_CLOSED ->
                        nextNode -> {
                            var start = Search.upperBound(nextNode.keys(), range.from());
                            var end = Search.floor(nextNode.keys(), range.to());
                            if (end < 0) {
                                return Optional.empty();
                            }
                            return Optional.of(new LeafIterator<>(nextNode, mapper, start, end));
                        };
            };
            case Direction.DESC -> switch (range.type()) {
                case RangeType.CLOSED, RangeType.CLOSED_OPEN ->
                        nextNode -> {
                            var start = Search.lowerBound(nextNode.keys(), range.from());
                            if (start >= nextNode.keys().size()) {
                                return Optional.empty();
                            }
                            return Optional.of(new ReverseLeafIterator<>(nextNode, mapper, start));
                        };
                case RangeType.OPEN, RangeType.OPEN_CLOSED ->
                        nextNode -> {
                            var start = Search.upperBound(nextNode.keys(), range.from());
                            if (start >= nextNode.keys().size()) {
                                return Optional.empty();
                            }
                            return Optional.of(new ReverseLeafIterator<>(nextNode, mapper, start));
                        };
            };
        };
    }


    @Override
    public boolean hasNext() {
        while (true) {
            if (leafEntries.hasNext()) {
                return true;
            }

            var nextLeafEntries = nextLeafEntries();
            if (nextLeafEntries.isEmpty()) {
                return false;
            }

            leafEntries = nextLeafEntries.get();
        }
    }

    /**
     * Advances the path stack to the next leaf and returns a bounded entry iterator
     * for it, or {@link Optional#empty()} if there are no more leaves in the range.
     *
     * @return bounded entry iterator for the next leaf, or empty if exhausted
     */
    private Optional<Iterator<E>> nextLeafEntries() {
        while (!path.isEmpty() && !path.getLast().hasNext()) {
            path.removeLast();
        }

        if (path.isEmpty()) {
            return Optional.empty();
        }

        var node = path.getLast().next();
        while (node instanceof Node.Internal<K,V> nextNode) {
            var it = nodePathMapper.apply(nextNode);
            path.add(it);
            node = it.next();
        }

        assert node instanceof Node.Leaf<K, V>;
        return leafIteratorMapper.apply((Node.Leaf<K, V>) node);
    }


    @Override
    public E next() {
        if (!hasNext()) {
            throw new NoSuchElementException();
        }
        return leafEntries.next();
    }
}

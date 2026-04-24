package io.dsal.versioned.index.persistent.core;

import io.dsal.versioned.index.api.Direction;
import io.dsal.versioned.index.api.Range;
import io.dsal.versioned.index.api.RangeType;
import io.dsal.versioned.index.persistent.iterator.BTreeIterator;
import io.dsal.versioned.index.persistent.iterator.BoundedBTreeIterator;
import io.dsal.versioned.index.persistent.util.Search;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Read-only query engine for tree nodes.
 *
 * <p>This component implements point lookup, full traversal, bounded traversal,
 * and iterator creation for both ascending and descending order. It does not
 * mutate nodes and can be reused by snapshots and transactions.
 *
 * @param <K> key type
 * @param <V> value type
 */
public class ReadQuery<K, V> {
    /* ==================== LOOKUP ==================== */

    /**
     * Returns whether {@code key} exists in the subtree rooted at {@code node}.
     *
     * @param node subtree root, or {@code null} for empty tree
     * @param key key to test
     * @return {@code true} if present
     */
    public boolean contains(Node<K, V> node, K key) {
        if (node == null) {
            return false;
        }

        return switch (node) {
            case Node.Internal<K, V> internal -> {
                var childIdx = Search.upperBound(internal.keys(), key);
                yield contains(internal.children().child(childIdx), key);
            }
            case Node.Leaf<K, V> leaf -> Search.find(leaf.keys(), key) >= 0;

        };
    }

    /**
     * Returns the value mapped to {@code key} in the subtree rooted at {@code node}.
     *
     * @param node subtree root, or {@code null} for empty tree
     * @param key key to resolve
     * @return optional value for the key
     */
    public Optional<V> get(Node<K, V> node, K key) {
        if (node == null) {
            return Optional.empty();
        }

        return switch (node) {
            case Node.Internal<K, V> internal -> {
                var childIdx = Search.upperBound(internal.keys(), key);
                yield get(internal.children().child(childIdx), key);
            }
            case Node.Leaf<K, V> leaf -> {
                var idx = Search.find(leaf.keys(), key);
                yield Optional.ofNullable(idx >= 0 ? leaf.values().val(idx) : null);
            }
        };
    }

    /* ==================== CONSUME RANGE ==================== */

    /**
     * Applies {@code consumer} to entries in {@code range} and {@code direction}.
     *
     * @param node subtree root, or {@code null} for empty tree
     * @param direction traversal direction
     * @param range range bounds and endpoint policy
     * @param consumer action to apply for each entry
     */
    public void forEach(Node<K, V> node, Direction direction, Range<K> range, BiConsumer<K, V> consumer) {
        if (node == null) {
            return;
        }
        switch (direction) {
            case Direction.ASC -> forEach(
                    node,
                    internalStart(range),
                    internalEnd(range),
                    leafStart(range),
                    leafEnd(range),
                    consumer
            );
            case Direction.DESC -> forEachReverse(
                    node,
                    internalStart(range),
                    internalEnd(range),
                    leafStart(range),
                    leafEnd(range),
                    consumer
            );
        }
    }


    /**
     * Returns a function that computes the first child index to visit in an internal
     * node based on the range start bound. Uses lower-bound for closed starts and
     * upper-bound for open starts.
     *
     * @param range range whose start bound and type drive the index selection
     * @return function from internal node to start child index (inclusive)
     */
    private Function<Node.Internal<K, V>, Integer> internalStart(Range<K> range) {
        return switch (range.type()) {
            case RangeType.CLOSED, RangeType.CLOSED_OPEN ->
                    node -> Search.lowerBound(node.keys(), range.from());

            case RangeType.OPEN, RangeType.OPEN_CLOSED  ->
                    node -> Search.upperBound(node.keys(), range.from());
        };
    }

    /**
     * Returns a function that computes the last child index to visit in an internal
     * node based on the range end bound. Uses upper-bound for closed ends and
     * lower-bound for open ends.
     *
     * @param range range whose end bound and type drive the index selection
     * @return function from internal node to end child index (inclusive)
     */
    private Function<Node.Internal<K, V>, Integer> internalEnd(Range<K> range) {
        return switch (range.type()) {
            case RangeType.CLOSED, RangeType.OPEN_CLOSED ->
                    node -> Search.upperBound(node.keys(), range.to());

            case RangeType.OPEN, RangeType.CLOSED_OPEN ->
                    node -> Search.lowerBound(node.keys(), range.to());
        };
    }

    /**
     * Returns a function that computes the first key index to yield from a leaf
     * based on the range start bound. Uses lower-bound for closed starts and
     * upper-bound for open starts.
     *
     * @param range range whose start bound and type drive the index selection
     * @return function from leaf to start key index (inclusive)
     */
    private Function<Node.Leaf<K, V>, Integer> leafStart(Range<K> range) {
        return switch (range.type()) {
            case RangeType.CLOSED, RangeType.CLOSED_OPEN ->
                    node -> Search.lowerBound(node.keys(), range.from());

            case RangeType.OPEN, RangeType.OPEN_CLOSED  ->
                    node -> Search.upperBound(node.keys(), range.from());
        };
    }

    /**
     * Returns a function that computes the last key index to yield from a leaf
     * based on the range end bound. Uses {@link Search#floor} for closed ends
     * (last key {@code <= to}) and {@link Search#predecessor} for open ends
     * (last key {@code < to}).
     *
     * @param range range whose end bound and type drive the index selection
     * @return function from leaf to end key index (inclusive); may be {@code -1}
     *         if no key in the leaf satisfies the bound
     */
    private Function<Node.Leaf<K, V>, Integer> leafEnd(Range<K> range) {
        return switch (range.type()) {
            case RangeType.CLOSED, RangeType.OPEN_CLOSED ->
                    node -> Search.floor(node.keys(), range.to());

            case RangeType.OPEN, RangeType.CLOSED_OPEN ->
                    node -> Search.predecessor(node.keys(), range.to());
        };
    }

    /**
     * Recursive ascending range traversal. Internal nodes iterate children from
     * {@code internalStart} to {@code internalEnd} (inclusive); leaves iterate
     * entries from {@code leafStart} to {@code leafEnd} (inclusive).
     *
     * @param node          subtree root
     * @param internalStart computes first child index for an internal node
     * @param internalEnd   computes last child index for an internal node
     * @param leafStart     computes first key index for a leaf
     * @param leafEnd       computes last key index for a leaf
     * @param consumer      action applied to each entry
     */
    private void forEach(
            Node<K, V> node,
            Function<Node.Internal<K, V>, Integer> internalStart,
            Function<Node.Internal<K, V>, Integer> internalEnd,
            Function<Node.Leaf<K, V>, Integer> leafStart,
            Function<Node.Leaf<K, V>, Integer> leafEnd,
            BiConsumer<K, V> consumer
    ) {
        switch (node) {
            case Node.Internal<K, V> internal -> {
                for (var idx = internalStart.apply(internal); idx <= internalEnd.apply(internal); idx++) {
                    forEach(
                            internal.children().child(idx),
                            internalStart,
                            internalEnd,
                            leafStart,
                            leafEnd,
                            consumer
                    );
                }
            }
            case Node.Leaf<K, V> leaf -> {
                for (var idx = leafStart.apply(leaf); idx <= leafEnd.apply(leaf); idx++) {
                    consumer.accept(leaf.keys().key(idx), leaf.values().val(idx));
                }
            }
        }
    }


    /**
     * Recursive descending range traversal. Internal nodes iterate children from
     * {@code internalEnd} down to {@code internalStart} (inclusive); leaves iterate
     * entries from {@code leafEnd} down to {@code leafStart} (inclusive).
     *
     * @param node          subtree root
     * @param internalStart computes lowest child index for an internal node
     * @param internalEnd   computes highest child index for an internal node
     * @param leafStart     computes lowest key index for a leaf
     * @param leafEnd       computes highest key index for a leaf
     * @param consumer      action applied to each entry
     */
    private void forEachReverse(
            Node<K, V> node,
            Function<Node.Internal<K, V>, Integer> internalStart,
            Function<Node.Internal<K, V>, Integer> internalEnd,
            Function<Node.Leaf<K, V>, Integer> leafStart,
            Function<Node.Leaf<K, V>, Integer> leafEnd,
            BiConsumer<K, V> consumer
    ) {
        switch (node) {
            case Node.Internal<K, V> internal -> {
                for (var idx = internalEnd.apply(internal); idx >= internalStart.apply(internal); idx--) {
                    forEachReverse(
                            internal.children().child(idx),
                            internalStart,
                            internalEnd,
                            leafStart,
                            leafEnd,
                            consumer
                    );
                }
            }
            case Node.Leaf<K, V> leaf -> {
                for (var idx = leafEnd.apply(leaf); idx >= leafStart.apply(leaf); idx--) {
                    consumer.accept(leaf.keys().key(idx), leaf.values().val(idx));
                }
            }
        }
    }

    /**
     * Applies {@code consumer} to all entries in {@code direction}.
     *
     * @param node subtree root, or {@code null} for empty tree
     * @param direction traversal direction
     * @param consumer action to apply for each entry
     */
    public void forEach(Node<K, V> node, Direction direction, BiConsumer<K, V> consumer) {
        if (node == null) {
            return;
        }

        switch (direction) {
            case Direction.ASC -> forEach(node, consumer);
            case Direction.DESC -> forEachReverse(node, consumer);
        }
    }


    /**
     * Recursive ascending full-tree traversal; visits all children and leaf entries
     * in ascending order.
     *
     * @param node     subtree root
     * @param consumer action applied to each entry
     */
    private void forEach(Node<K, V> node, BiConsumer<K, V> consumer) {
        switch (node) {
            case Node.Internal<K, V> internal -> {
                for (var idx = 0; idx < internal.children().size(); idx++) {
                    forEach(internal.children().child(idx), consumer);
                }
            }
            case Node.Leaf<K, V> leaf -> {
                for (var idx = 0; idx < leaf.keys().size(); idx++) {
                    consumer.accept(leaf.keys().key(idx), leaf.values().val(idx));
                }
            }
        }
    }

    /**
     * Recursive descending full-tree traversal; visits all children and leaf entries
     * in descending order.
     *
     * @param node     subtree root
     * @param consumer action applied to each entry
     */
    private void forEachReverse(Node<K, V> node, BiConsumer<K, V> consumer) {
        switch (node) {
            case Node.Internal<K, V> internal -> {
                for (var idx = internal.children().size() - 1; idx >= 0; idx--) {
                    forEachReverse(internal.children().child(idx), consumer);
                }
            }
            case Node.Leaf<K, V> leaf -> {
                for (var idx = leaf.keys().size() - 1; idx >= 0; idx--) {
                    consumer.accept(leaf.keys().key(idx), leaf.values().val(idx));
                }
            }
        }
    }

    /* ==================== ITERATION ==================== */

    /**
     * Returns an iterator over the subtree in {@code direction}.
     *
     * @param node subtree root, or {@code null} for empty tree
     * @param direction traversal direction
     * @param mapper output mapping for each key-value pair
     * @param <E> iterator element type
     * @return iterator over all entries in direction order
     */
    public <E> Iterator<E> iterator(Node<K, V> node, Direction direction, BiFunction<K, V, E> mapper) {
        if (node == null) {
            return Collections.emptyIterator();
        }
        return BTreeIterator.of(node, direction, mapper);
    }


    /**
     * Returns an iterator over entries in {@code range} and {@code direction}.
     *
     * @param node subtree root, or {@code null} for empty tree
     * @param direction traversal direction
     * @param range range bounds and endpoint policy
     * @param mapper output mapping for each key-value pair
     * @param <E> iterator element type
     * @return iterator over range-constrained entries in direction order
     */
    public <E> Iterator<E> iterator(Node<K, V> node, Direction direction, Range<K> range, BiFunction<K, V, E> mapper) {
        if (node == null) {
            return Collections.emptyIterator();
        }
        return BoundedBTreeIterator.of(node, direction, range, mapper);
    }
}

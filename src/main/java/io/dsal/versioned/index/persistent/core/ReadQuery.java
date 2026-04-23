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

public class ReadQuery<K, V> {
    /* ==================== LOOKUP ==================== */

    public boolean contains(Node<K, V> node, K key) {
        return switch (node) {
            case Node.Internal<K, V> internal -> {
                var childIdx = Search.upperBound(internal.keys(), key);
                yield contains(internal.children().child(childIdx), key);
            }
            case Node.Leaf<K, V> leaf -> Search.find(leaf.keys(), key) >= 0;

        };
    }

    public Optional<V> get(Node<K, V> node, K key) {
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

    public void forEach(Node<K, V> node, Direction direction, Range<K> range, BiConsumer<K, V> consumer) {
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


    private Function<Node.Internal<K, V>, Integer> internalStart(Range<K> range) {
        return switch (range.type()) {
            case RangeType.CLOSED, RangeType.CLOSED_OPEN ->
                    node -> Search.lowerBound(node.keys(), range.from());

            case RangeType.OPEN, RangeType.OPEN_CLOSED  ->
                    node -> Search.upperBound(node.keys(), range.from());
        };
    }

    private Function<Node.Internal<K, V>, Integer> internalEnd(Range<K> range) {
        return switch (range.type()) {
            case RangeType.CLOSED, RangeType.OPEN_CLOSED ->
                    node -> Search.upperBound(node.keys(), range.to());

            case RangeType.OPEN, RangeType.CLOSED_OPEN ->
                    node -> Search.lowerBound(node.keys(), range.to());
        };
    }

    private Function<Node.Leaf<K, V>, Integer> leafStart(Range<K> range) {
        return switch (range.type()) {
            case RangeType.CLOSED, RangeType.CLOSED_OPEN ->
                    node -> Search.lowerBound(node.keys(), range.from());

            case RangeType.OPEN, RangeType.OPEN_CLOSED  ->
                    node -> Search.upperBound(node.keys(), range.from());
        };
    }

    private Function<Node.Leaf<K, V>, Integer> leafEnd(Range<K> range) {
        return switch (range.type()) {
            case RangeType.CLOSED, RangeType.OPEN_CLOSED ->
                    node -> Search.floor(node.keys(), range.to());

            case RangeType.OPEN, RangeType.CLOSED_OPEN ->
                    node -> Search.predecessor(node.keys(), range.to());
        };
    }

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
                    forEach(internal.children().child(idx), consumer);
                }
            }
            case Node.Leaf<K, V> leaf -> {
                for (var idx = leafStart.apply(leaf); idx <= leafEnd.apply(leaf); idx++) {
                    consumer.accept(leaf.keys().key(idx), leaf.values().val(idx));
                }
            }
        }
    }


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
                    forEach(internal.children().child(idx), consumer);
                }
            }
            case Node.Leaf<K, V> leaf -> {
                for (var idx = leafEnd.apply(leaf); idx >= leafStart.apply(leaf); idx--) {
                    consumer.accept(leaf.keys().key(idx), leaf.values().val(idx));
                }
            }
        }
    }

    public void forEach(Node<K, V> node, Direction direction, BiConsumer<K, V> consumer) {
        switch (direction) {
            case Direction.ASC -> forEach(node, consumer);
            case Direction.DESC -> forEachReverse(node, consumer);
        }
    }


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

    private void forEachReverse(Node<K, V> node, BiConsumer<K, V> consumer) {
        switch (node) {
            case Node.Internal<K, V> internal -> {
                for (var idx = internal.children().size() - 1; idx >= 0; idx--) {
                    forEach(internal.children().child(idx), consumer);
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

    public <E> Iterator<E> iterator(Node<K, V> node, Direction direction, BiFunction<K, V, E> mapper) {
        return BTreeIterator.of(node, direction, mapper);
    }


    public <E> Iterator<E> iterator(Node<K, V> node, Direction direction, Range<K> range, BiFunction<K, V, E> mapper) {
        return BoundedBTreeIterator.of(node, direction, range, mapper);
    }
}

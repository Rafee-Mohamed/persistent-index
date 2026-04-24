package io.dsal.versioned.index.persistent.iterator;

import io.dsal.versioned.index.api.Direction;
import io.dsal.versioned.index.api.Range;
import io.dsal.versioned.index.api.RangeType;
import io.dsal.versioned.index.persistent.core.Node;
import io.dsal.versioned.index.persistent.util.Search;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.function.BiFunction;
import java.util.function.Function;

public class BoundedBTreeIterator<K, V, E> implements Iterator<E> {

    private final List<Iterator<Node<K, V>>> path;

    private final Function<Node.Internal<K, V>, Iterator<Node<K, V>>> nodePathMapper;

    private final Function<Node.Leaf<K, V>, Iterator<E>>  leafIteratorMapper;

    private Iterator<E> leafEntries;

    private BoundedBTreeIterator(
            List<Iterator<Node<K, V>>> path,
            Function<Node.Internal<K, V>, Iterator<Node<K, V>>> nodePathMapper,
            Function<Node.Leaf<K, V>, Iterator<E>>  leafIteratorMapper,
            Iterator<E> leafEntries
    ) {
        this.path = path;
        this.nodePathMapper = nodePathMapper;
        this.leafIteratorMapper = leafIteratorMapper;
        this.leafEntries = leafEntries;
    }

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

        // nothing falls into the range(from, to)
        if (!leafEntries.hasNext()) {
            path.clear();
        }

        return new BoundedBTreeIterator<>(path, nodePathMapper, leafIteratorMapper, leafEntries);
    }

    private static <K, V> Function<Node.Internal<K, V>, Iterator<Node<K, V>>> nodeBoundedPathMapper(Direction direction, Range<K> range) {
        return switch (direction) {
            case Direction.ASC -> switch (range.type()) {
                case RangeType.OPEN, RangeType.OPEN_CLOSED ->
                        nextNode -> new NodeIterator<>(nextNode, Search.upperBound(nextNode.keys(), range.from()));
                case RangeType.CLOSED, RangeType.CLOSED_OPEN ->
                        nextNode -> new NodeIterator<>(nextNode, Search.lowerBound(nextNode.keys(), range.from()));
            };
            case Direction.DESC -> switch (range.type()) {
                case RangeType.CLOSED, RangeType.OPEN_CLOSED ->
                        nextNode -> new ReverseNodeIterator<>(nextNode, Search.upperBound(nextNode.keys(), range.to()));
                case RangeType.OPEN, RangeType.CLOSED_OPEN ->
                        nextNode -> new ReverseNodeIterator<>(nextNode, Search.lowerBound(nextNode.keys(), range.to()));
            };
        };
    }

    private static <K, V, E> Function<Node.Leaf<K, V>, Iterator<E>>  leafIteratorMapper(Direction direction, Range<K> range, BiFunction<K, V, E> mapper) {
        return switch (direction) {
            case Direction.ASC -> switch (range.type()) {
                case RangeType.OPEN, RangeType.CLOSED_OPEN ->
                        nextNode -> new LeafIterator<>(nextNode, mapper, Search.predecessor(nextNode.keys(), range.to()));
                case RangeType.CLOSED, RangeType.OPEN_CLOSED ->
                        nextNode -> new LeafIterator<>(nextNode, mapper, Search.floor(nextNode.keys(), range.to()));
            };
            case Direction.DESC -> switch (range.type()) {
                case RangeType.CLOSED, RangeType.CLOSED_OPEN ->
                        nextNode -> new ReverseLeafIterator<>(nextNode, mapper, Search.lowerBound(nextNode.keys(), range.from()));
                case RangeType.OPEN, RangeType.OPEN_CLOSED ->
                        nextNode -> new ReverseLeafIterator<>(nextNode, mapper, Search.upperBound(nextNode.keys(), range.from()));
            };
        };
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

        if (!leafEntries.hasNext()) {
            path.clear();
            return false;
        }

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

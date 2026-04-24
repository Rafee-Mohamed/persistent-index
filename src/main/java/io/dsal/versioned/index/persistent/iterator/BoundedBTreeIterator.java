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
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;

public class BoundedBTreeIterator<K, V, E> implements Iterator<E> {

    private final List<Iterator<Node<K, V>>> path;

    private final Function<Node.Internal<K, V>, Iterator<Node<K, V>>> nodePathMapper;

    private final Function<Node.Leaf<K, V>, Optional<Iterator<E>>>  leafIteratorMapper;

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

    private static <K, V> Function<Node.Internal<K, V>, Iterator<Node<K, V>>> nodeBoundedPathMapper(Direction direction, Range<K> range) {
        return switch (direction) {
            case Direction.ASC ->
                    nextNode -> new NodeIterator<>(nextNode, Search.lowerBound(nextNode.keys(), range.from()));
            case Direction.DESC ->
                    nextNode -> new ReverseNodeIterator<>(nextNode, Search.upperBound(nextNode.keys(), range.to()));
        };
    }

    private static <K, V, E> Function<Node.Leaf<K, V>, Optional<Iterator<E>>>  leafIteratorMapper(Direction direction, Range<K> range, BiFunction<K, V, E> mapper) {
        return switch (direction) {
            case Direction.ASC -> switch (range.type()) {
                case RangeType.OPEN, RangeType.CLOSED_OPEN ->
                        nextNode -> {
                            var end = Search.predecessor(nextNode.keys(), range.to());
                            if (end < 0) {
                                return Optional.empty();
                            }
                            return Optional.of(new LeafIterator<>(nextNode, mapper, end));
                        };
                case RangeType.CLOSED, RangeType.OPEN_CLOSED ->
                        nextNode -> {
                            var end = Search.floor(nextNode.keys(), range.to());
                            if (end < 0) {
                                return Optional.empty();
                            }
                            return Optional.of(new LeafIterator<>(nextNode, mapper, end));
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

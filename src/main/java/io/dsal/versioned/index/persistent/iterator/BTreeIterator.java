package io.dsal.versioned.index.persistent.iterator;

import io.dsal.versioned.index.api.Direction;
import io.dsal.versioned.index.persistent.core.Node;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.function.BiFunction;
import java.util.function.Function;


public class BTreeIterator<K, V, E> implements Iterator<E> {

    private final List<Iterator<Node<K, V>>> path;

    private final Function<Node.Internal<K, V>, Iterator<Node<K, V>>> nodePathMapper;

    private final Function<Node.Leaf<K, V>, Iterator<E>>  leafIteratorMapper;

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

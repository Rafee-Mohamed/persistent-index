package io.dsal.versioned.index.persistent.iterator;

import io.dsal.versioned.index.core.KeyVal;
import io.dsal.versioned.index.persistent.core.Node;
import io.dsal.versioned.index.core.PersistentBPlusTree;
import io.dsal.versioned.index.persistent.layout.IndexedComparator;
import io.dsal.versioned.index.persistent.util.Search;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * Iterator over keys in {@code [from, to]} inclusive (ascending), using
 * {@link Search#lowerBound(IndexedComparator, Object)}
 * on each internal node's keys to descend toward {@code from} and
 * to bound each leaf. When a leaf is exhausted, advances to the next leaf that can
 * still contain keys {@code <= to}.
 *
 * <p><b>Snapshot:</b> Uses the root passed to {@link #of} only; does not observe later
 * mutations. Same empty cases as {@link PersistentBPlusTree#rangeIterator(Object, Object)}
 * (erasure of {@code range(K, K)}):
 * no keys in the interval, or {@code from} after {@code to} in key order.</p>
 *
 * @param <K> key type
 * @param <V> value type
 * @see PersistentBPlusTree#rangeIterator(Object, Object)
 */
public class BoundedBTreeIterator<K, V> implements Iterator<KeyVal<K, V>> {

    /** Ancestors from root toward {@link #currentLeaf}; each {@link Frame} tracks the next sibling. */
    private final List<Frame<K, V>> path;

    /** Leaf currently being scanned. */
    private Node.Leaf<K, V> currentLeaf;

    /** Next index in this leaf (inclusive lower of the range on this leaf). */
    private int currentIdx;

    /** Last index in this leaf that is still {@code <= to} (inclusive). */
    private int endIdx;

    /** Inclusive upper bound; recomputed when moving to the next leaf. */
    private final K to;

    private BoundedBTreeIterator(List<Frame<K, V>> path, Node.Leaf<K, V> leaf, int leafStart, int leafEnd, K to) {
        this.path = path;
        this.currentLeaf = leaf;
        this.currentIdx = leafStart;
        this.endIdx = leafEnd;
        this.to = to;
    }


    /**
     * Starts at the first key {@code >= from} in {@code node}'s subtree (if any), and
     * stops before keys {@code > to}.
     *
     * @param <K> key type
     * @param <V> value type
     * @param node subtree root (typically {@link PersistentBPlusTree}'s root; must not be {@code null})
     * @param from inclusive lower bound
     * @param to   inclusive upper bound
     * @return iterator; may yield no elements if the range is empty
     */
    public static <K, V> BoundedBTreeIterator<K, V> of(Node<K, V> node, K from, K to) {
        var path = new ArrayList<Frame<K, V>>();
        while (node instanceof Node.Internal<K,V> next) {
            var lb = Search.lowerBound(next.keys(), from);
            var frame = new Frame<>(next, lb.idx());
            path.add(frame);
            node = frame.next();
        }

        assert node instanceof Node.Leaf<K, V>;
        var leaf = (Node.Leaf<K, V>) node;

        var start = Search.lowerBound(leaf.keys(), from).idx();
        var endLb = Search.lowerBound(leaf.keys(), to);
        var end = endLb.found() ? endLb.idx() : endLb.idx() - 1;

        return new BoundedBTreeIterator<>(path, leaf, start, end, to);
    }


    @Override
    public boolean hasNext() {
        if (currentIdx <= endIdx) {
            return true;
        }

        if (path.isEmpty()) {
            return false;
        }

        var node = path.getLast().next();
        while (!path.isEmpty() && node == null) {
            path.removeLast();
            if (path.isEmpty()) {
                break;
            }
            node = path.getLast().next();
        }

        if (path.isEmpty()) {
            return false;
        }

        while (node instanceof Node.Internal<K,V> nextNode) {
            var frame = new Frame<>(nextNode, 0);
            path.add(frame);
            node = frame.next();
        }

        assert node instanceof Node.Leaf<K, V>;
        var nextLeaf = (Node.Leaf<K, V>) node;

        var endLb = Search.lowerBound(nextLeaf.keys(), to);
        var end = endLb.found() ? endLb.idx() : endLb.idx() - 1;

        if (end < 0) {
            path.clear();
            return false;
        }

        currentLeaf = nextLeaf;
        currentIdx = 0;
        endIdx = end;

        return true;
    }

    /**
     * @return next key–value pair in {@code [from, to]}
     * @throws NoSuchElementException if there is no next element
     */
    @Override
    public KeyVal<K, V> next() {
        if (!hasNext()) {
            throw new NoSuchElementException();
        }
        var idx = currentIdx++;
        return new KeyVal<>(currentLeaf.keys().key(idx), currentLeaf.values().val(idx));
    }
}

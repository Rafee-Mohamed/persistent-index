package io.dsal.versioned.index.persistent.iterator;

import io.dsal.versioned.index.core.KeyVal;
import io.dsal.versioned.index.persistent.core.Node;
import io.dsal.versioned.index.core.PersistentBPlusTree;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * In-order iterator over every {@link KeyVal} in a B+ subtree: leftmost leaf first,
 * then scan along leaves using an internal-node stack ({@code path}) to find the
 * next leaf when the current one is exhausted.
 *
 * <p><b>Snapshot:</b> {@link #of} is given a fixed root; it does not see later
 * structural updates to the tree. Used by {@link PersistentBPlusTree#iterator()}.</p>
 *
 * @param <K> key type
 * @param <V> value type
 * @see PersistentBPlusTree#iterator()
 */
public class BTreeIterator<K, V> implements Iterator<KeyVal<K, V>> {

    /** Ancestors from root toward {@link #currentLeaf}; each {@link Frame} tracks the next sibling. */
    private final List<Frame<K, V>> path;

    /** Leaf whose keys are being emitted. */
    private Node.Leaf<K, V> currentLeaf;

    /** Index of the next key in the current leaf ({@link Node.Leaf#keys()}). */
    private int currentIdx;

    private BTreeIterator(List<Frame<K, V>> path, Node.Leaf<K, V> leaf) {
        this.path = path;
        this.currentLeaf = leaf;
        this.currentIdx = 0;
    }


    /**
     * Positions at the smallest key in {@code node} by descending the left spine to
     * the first leaf.
     *
     * @param <K> key type
     * @param <V> value type
     * @param node subtree root (typically {@link PersistentBPlusTree}'s root; must not be {@code null})
     * @return iterator over that subtree in ascending key order
     */
    public static <K, V> BTreeIterator<K, V> of(Node<K, V> node) {
        var path = new ArrayList<Frame<K, V>>();
        while (node instanceof Node.Internal<K,V> next) {
            var frame = new Frame<>(next);
            path.add(frame);
            node = frame.next();
        }

        assert node instanceof Node.Leaf<K, V>;
        var leaf = (Node.Leaf<K, V>) node;

        return new BTreeIterator<>(path, leaf);
    }


    @Override
    public boolean hasNext() {
        if (currentIdx < currentLeaf.keys().size()) {
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
            var frame = new Frame<>(nextNode);
            path.add(frame);
            node = frame.next();
        }

        assert node instanceof Node.Leaf<K, V>;
        currentLeaf = (Node.Leaf<K, V>) node;
        currentIdx = 0;

        return true;
    }

    /**
     * @return next key–value pair in ascending order
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

package io.dsal.versioned.index.core;

import java.util.Arrays;

/**
 * Child pointers for an {@link Node.Internal} node (copy-on-write). Updated with
 * {@link io.dsal.versioned.index.layout.KeyStorage} in {@link PersistentBPlusTree};
 * {@link #insertAndSplit} returns {@link ChildrenSplit} when the parent overflows.
 *
 * <pre>
 *   Parent (internal):   keys   [ k0 | k1 | k2 ]
 *                        ch     c0   c1   c2   c3     index 0..size-1 ; size = keys + 1
 * </pre>
 *
 * <p>Insert index {@code idx} is only {@code [0, size())} (no slot {@code size});
 * unlike {@link io.dsal.versioned.index.layout.KeyStorage#insert(int, Object)}.
 * Leaves use {@link io.dsal.versioned.index.layout.ValueStorage}.</p>
 *
 * @param <K> key type
 * @param <V> value type
 * @see Node.Internal
 * @see ChildrenSplit
 */
public class Children<K, V> {
    private final Node<K, V>[] nodes;

    private Children(Node<K, V>[] nodes) {
        this.nodes = nodes;
    }

    /**
     * Minimal internal node: one separator key, two children.
     *
     * <pre>
     *   keys [ k ]     ch   L    R
     * </pre>
     *
     * @param left  low-side subtree
     * @param right high-side subtree
     * @return {@code Children} with {@code size() == 2}
     */
    static <K, V> Children<K, V> of(Node<K, V> left, Node<K, V> right) {
        return new Children<K, V>(new Node[]{left, right});
    }

    /**
     * Returns the number of child pointers (for the owning {@link Node.Internal},
     * equals {@code keys.size() + 1}).
     *
     * @return child count
     */
    public int size() {
        return nodes.length;
    }

    /**
     * Returns the subtree root at {@code idx}.
     *
     * @param idx index in {@code [0, size())}
     * @return child node at {@code idx}
     * @throws IndexOutOfBoundsException if {@code idx} is out of range
     */
    public Node<K, V> child(int idx) {
        checkIndexBounds(idx);
        return nodes[idx];
    }

    /**
     * Replaces one pointer; length unchanged.
     *
     * <pre>
     *   [ c0 | c1 | c2 ]  replace(1, N)  --&gt;  [ c0 | N | c2 ]
     * </pre>
     *
     * @param idx  index in {@code [0, size())}
     * @param node new subtree for that slot
     * @return new {@code Children} with updated pointer at {@code idx}
     * @throws IndexOutOfBoundsException if {@code idx} is out of range
     */
    Children<K, V> replace(int idx, Node<K, V> node) {
        checkIndexBounds(idx);

        var newNodes = Arrays.copyOf(nodes, nodes.length);
        newNodes[idx] = node;
        return new Children<>(newNodes);
    }

    /**
     * Inserts at {@code idx}, shifting right. {@code idx} in {@code [0, size())} only
     * (no append at {@code size}; unlike {@link io.dsal.versioned.index.layout.KeyStorage#insert(int, Object)}).
     *
     * <pre>
     *   Before:  [ c0 | c1 | c2 | c3 ]     size 4
     *   insert(2, X)  --&gt;  [ c0 | c1 | X | c2 | c3 ]
     * </pre>
     *
     * @param idx  insertion position in {@code [0, size())}
     * @param node subtree to insert
     * @return new {@code Children} whose {@link #size()} is {@code this.size() + 1}
     * @throws IndexOutOfBoundsException if {@code idx} is not valid for insert
     */
    Children<K, V> insert(int idx, Node<K, V> node) {
        checkInsertBounds(idx);

        var newNodes = (Node<K,V>[]) new Node[nodes.length + 1];

        System.arraycopy(nodes, 0, newNodes, 0, idx);
        newNodes[idx] = node;
        System.arraycopy(nodes, idx, newNodes, idx + 1, nodes.length - idx);

        return new Children<>(newNodes);
    }

    /**
     * One child at {@code idx} becomes two ({@code left}, {@code right}); tail shifts
     * right; net +1 pointer (child split).
     *
     * <pre>
     *   Before:  [ c0 | C | c2 | c3 ]     C overflowed
     *   insert(1, L, R)  --&gt;  [ c0 | L | R | c2 | c3 ]
     * </pre>
     *
     * @param idx   in {@code [0, size())}; same as {@link #insert(int, Node)}
     * @param left  lower split of old child
     * @param right higher split of old child
     * @return new {@code Children} whose {@link #size()} is {@code this.size() + 1}
     * @throws IndexOutOfBoundsException if {@code idx} is not valid for insert
     */
    Children<K, V> insert(int idx, Node<K, V> left, Node<K, V> right) {
        checkInsertBounds(idx);

        var newNodes = (Node<K,V>[]) new Node[nodes.length + 1];

        System.arraycopy(nodes, 0, newNodes, 0, idx);
        newNodes[idx] = left;
        newNodes[idx + 1] = right;
        System.arraycopy(nodes, idx + 1, newNodes, idx + 2, nodes.length - idx - 1);

        return new Children<>(newNodes);
    }

    /**
     * Replaces two adjacent slots only (rebalance / borrow).
     *
     * <pre>
     *   Before:  [ c0 | a | b | c3 ]
     *   replace(1, L, R)  --&gt;  [ c0 | L | R | c3 ]
     * </pre>
     *
     * @param idx   first slot; {@code idx + 1} must exist
     * @param left  new subtree at {@code idx}
     * @param right new subtree at {@code idx + 1}
     * @return new {@code Children} with the same size as this
     * @throws IndexOutOfBoundsException if {@code idx} or {@code idx + 1} is out of range
     */
    Children<K, V> replace(int idx, Node<K, V> left, Node<K, V> right) {
        checkIndexBounds(idx);
        checkIndexBounds(idx + 1);

        var newNodes = (Node<K,V>[]) new Node[nodes.length];

        System.arraycopy(nodes, 0, newNodes, 0, nodes.length);
        newNodes[idx] = left;
        newNodes[idx + 1] = right;

        return new Children<>(newNodes);
    }

    /**
     * Removes at {@code idx}; later slots shift left.
     *
     * <pre>
     *   Before:  [ c0 | c1 | c2 | c3 ]
     *   remove(1)  --&gt;  [ c0 | c2 | c3 ]
     * </pre>
     *
     * @param idx index to remove, in {@code [0, size())}
     * @return new {@code Children} whose {@link #size()} is {@code this.size() - 1}
     * @throws IndexOutOfBoundsException if {@code idx} is out of range
     */
    Children<K, V> remove(int idx) {
        checkIndexBounds(idx);

        var newNodes = (Node<K,V>[]) new Node[nodes.length - 1];

        System.arraycopy(nodes, 0, newNodes, 0, idx);
        System.arraycopy(nodes, idx + 1, newNodes, idx, nodes.length - idx - 1);

        return new Children<>(newNodes);
    }

    /**
     * Remove-then-write at {@code idx} (merge: one pointer fewer; parent drops a key too).
     *
     * <pre>
     *   Before:  [ c0 | c1 | c2 | c3 ]   size 4
     *   removeAndReplace(1, M)  --&gt;  [ c0 | M | c3 ]   size 3
     * </pre>
     *
     * @param idx  in {@code [0, size())}
     * @param node merged subtree placed at {@code idx} after the shift step
     * @return new {@code Children} whose {@link #size()} is {@code this.size() - 1}
     * @throws IndexOutOfBoundsException if {@code idx} is out of range
     */
    Children<K, V> removeAndReplace(int idx, Node<K, V> node) {
        checkIndexBounds(idx);

        var newNodes = (Node<K,V>[]) new Node[nodes.length - 1];

        System.arraycopy(nodes, 0, newNodes, 0, idx);
        System.arraycopy(nodes, idx + 1, newNodes, idx, nodes.length - idx - 1);
        newNodes[idx] = node;

        return new Children<>(newNodes);
    }

    /**
     * Concatenates child lists (internal-node merge).
     *
     * <pre>
     *   this:  [ a | b ]   other: [ c | d ]
     *   merge(other)  --&gt;  [ a | b | c | d ]
     * </pre>
     *
     * @param other appended after this sequence
     * @return new {@code Children} with {@code size() == this.size() + other.size()}
     */
    Children<K, V> merge(Children<K, V> other) {
        var otherNodes = other.nodes;
        var newNodes = (Node<K,V>[]) new Node[nodes.length + otherNodes.length];

        System.arraycopy(nodes, 0, newNodes, 0, nodes.length);
        System.arraycopy(otherNodes, 0, newNodes, nodes.length, otherNodes.length);

        return new Children<>(newNodes);
    }

    /**
     * Fused remove then insert; same permutation as
     * {@link io.dsal.versioned.index.layout.KeyStorage#removeAndInsert(int, int, Object)}.
     * Length unchanged.
     *
     * <pre>
     *   Example:  remove(1), insert(2, X) reorders like key/value {@code removeAndInsert}
     * </pre>
     *
     * @param removeIdx removed first; in {@code [0, size())}
     * @param insertIdx target index after removal step; in {@code [0, size())}
     * @param node      subtree to place
     * @return new {@code Children} whose {@link #size()} equals {@code this.size()}
     * @throws IndexOutOfBoundsException if {@code removeIdx} or {@code insertIdx} is out of range
     */
    Children<K, V> removeAndInsert(int removeIdx, int insertIdx, Node<K, V> node) {
        checkIndexBounds(removeIdx);
        checkIndexBounds(insertIdx);

        var newNodes = (Node<K, V>[]) new Node[nodes.length];

        // prefix is unchanged before insertIdx or removeIdx
        var unchangedPrefixEnd = Math.min(removeIdx, insertIdx);
        System.arraycopy(nodes, 0, newNodes, 0, unchangedPrefixEnd);

        if (insertIdx > removeIdx) {
            System.arraycopy(nodes, removeIdx + 1, newNodes, removeIdx, insertIdx - removeIdx);
            newNodes[insertIdx] = node;
            System.arraycopy(nodes, insertIdx + 1, newNodes, insertIdx + 1, nodes.length - insertIdx - 1);
        } else { // insert then remove, insertIdx == prefixEnd
            newNodes[insertIdx] = node;
            System.arraycopy(nodes, insertIdx, newNodes, insertIdx + 1, removeIdx - insertIdx);
            System.arraycopy(nodes, removeIdx + 1, newNodes, removeIdx, nodes.length - removeIdx - 1);
        }

        return new Children<>(newNodes);
    }

    /**
     * Child-slot analogue of
     * {@link io.dsal.versioned.index.layout.KeyStorage#insertAndSplit(int, int, Object)}:
     * insert split pair ({@code left}, {@code right}) at {@code insertIdx}, then cut
     * at {@code splitIdx}. Right slot of the pair is index {@code insertIdx + 1}.
     * Returns {@link ChildrenSplit}.
     *
     * <pre>
     *   Use the same {@code insertIdx} and {@code splitIdx} as the parent's
     *   {@link io.dsal.versioned.index.layout.KeyStorage#insertAndSplit(int, int, Object)}.
     *   The right half of the new pair is at child index {@code insertIdx + 1}.
     * </pre>
     *
     * @param insertIdx slot of overflowing child (same as key side)
     * @param splitIdx  cut in post-insert sequence; valid {@code 1 .. size()} (see checks)
     * @param left      lower node after child split
     * @param right     higher node after child split
     * @return two child partitions for the split parent
     * @throws IndexOutOfBoundsException if {@code insertIdx} or {@code splitIdx} is invalid
     */
    ChildrenSplit<K, V> insertAndSplit(int insertIdx, int splitIdx, Node<K, V> left, Node<K, V> right) {
        checkInsertBounds(insertIdx);
        checkSplitBounds(splitIdx);

        var insertIdxForRight = insertIdx + 1;
        if (insertIdxForRight >= splitIdx) {
            var leftNodes = (Node<K, V>[]) new Node[splitIdx];
            System.arraycopy(nodes, 0, leftNodes, 0, splitIdx);

            var rightNodes = (Node<K, V>[]) new Node[nodes.length - splitIdx + 1];
            var prefixLen = insertIdxForRight - splitIdx;
            var suffixLen = nodes.length - insertIdxForRight;

            System.arraycopy(nodes, splitIdx, rightNodes, 0, prefixLen);
            rightNodes[prefixLen] = right;
            System.arraycopy(nodes, insertIdxForRight, rightNodes, prefixLen + 1, suffixLen);

            // i.e. rightInsertIdx == splitIdx, therefore left is the last node of leftNodes
            if (prefixLen == 0) {
                leftNodes[leftNodes.length - 1] = left;
            } else {
                // left is part of rightNodes comes in last of the prefix length
                rightNodes[prefixLen - 1] = left;
            }

            return new ChildrenSplit<>(
                    new Children<>(leftNodes),
                    new Children<>(rightNodes)
            );
        }

        var leftNodes = (Node<K, V>[]) new Node[splitIdx];
        System.arraycopy(nodes, 0, leftNodes, 0, insertIdxForRight);
        leftNodes[insertIdx] = left;
        leftNodes[insertIdxForRight] = right;
        System.arraycopy(nodes, insertIdxForRight, leftNodes, insertIdxForRight + 1, splitIdx - insertIdxForRight - 1);

        var splitIdxAfterInsertion = splitIdx - 1;
        var rightNodes = (Node<K, V>[]) new Node[nodes.length - splitIdxAfterInsertion];
        System.arraycopy(nodes, splitIdxAfterInsertion, rightNodes, 0, rightNodes.length);

        return new ChildrenSplit<>(
                new Children<>(leftNodes),
                new Children<>(rightNodes)
        );
    }

    private void checkInsertBounds(int idx) {
        if (idx < 0 || idx > nodes.length) {
            throw new IndexOutOfBoundsException("Index " + idx + " is out of bounds for insert: " + "[" + 0 + " " + nodes.length + ")");
        }
    }

    private void checkSplitBounds(int idx) {
        if (idx <= 0 || idx > nodes.length) {
            throw new IndexOutOfBoundsException("Index " + idx + " is out of bounds for split: " + "(" + 0 + " " + nodes.length + "]");
        }
    }

    private void checkIndexBounds(int idx) {
        if (idx < 0 || idx >= nodes.length) {
            throw new IndexOutOfBoundsException("Index " + idx + " is out of bounds: " + "[" + 0 + " " + nodes.length + ")");
        }
    }


}

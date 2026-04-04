package io.dsal.persistent.index.core;


import io.dsal.persistent.index.layout.KeyStorageFactory;
import io.dsal.persistent.index.layout.KeyStorage;
import io.dsal.persistent.index.layout.ValueStorage;
import io.dsal.persistent.index.util.Search;
import io.dsal.persistent.index.iterator.BTreeIterator;
import io.dsal.persistent.index.iterator.BoundedBTreeIterator;


import java.util.*;
import java.util.function.Consumer;

/**
 * Persistent ordered index implemented as a copy-on-write B+ tree.
 *
 * <p>Updates never mutate existing {@link Node} instances. Each {@code put} or
 * {@code remove} builds fresh nodes along the affected root-to-leaf path and
 * reuses unchanged subtrees (structural sharing).</p>
 *
 * <h2>Model</h2>
 *
 * <ul>
 *   <li>Copy-on-write: no in-place node mutation</li>
 *   <li>Intended concurrency: single writer, multiple readers (SWMR)</li>
 *   <li>Each successful update publishes a new tree version by assigning
 *       {@link #root}</li>
 * </ul>
 *
 * <h2>Snapshot behavior</h2>
 *
 * <p>Read APIs that begin by loading {@code root} into a local variable
 * (and iterators created via {@link #iterator()} or {@link #rangeIterator(K, K)}},
 * which capture {@code root} at construction) operate on that fixed version
 * for their entire execution, even if another thread publishes a newer
 * {@code root}.</p>
 *
 * <pre>
 *   Writer:   R0 ----> R1 ----> R2
 *
 *   Readers holding R0 or R1 see a stable structure for the lifetime of
 *   their operation.
 * </pre>
 *
 * <h2>Structure</h2>
 *
 * <pre>
 *   Internal node:
 *     keys:     [K1 | K2 | K3]
 *     children: [C0  C1  C2  C3]
 *
 *   Leaf node:
 *     keys:   [K1 | K2 | K3]
 *     values: [V1 | V2 | V3]
 * </pre>
 *
 * <p>Internal keys are separators: values exist only in leaves. Descent uses
 * {@link Search#lowerBound}; if the search hits a separator exactly, the next
 * child index is one past that key (see {@link #get(Node, Object)}).</p>
 *
 * <h2>Invariants</h2>
 *
 * <ul>
 *   <li>Keys are ordered according to {@link KeyStorage}</li>
 *   <li>Internal nodes: {@code children.size() == keys.size() + 1}</li>
 *   <li>Leaf nodes: {@code values.size() == keys.size()}</li>
 *   <li>All leaves at equal depth</li>
 *   <li>At most {@code maxKeys} keys per node; non-root nodes have at least
 *       {@code minKeys == maxKeys / 2} keys when non-empty, except as relaxed
 *       for the root during deletion normalization</li>
 * </ul>
 *
 * <h2>Update semantics</h2>
 *
 * <ul>
 *   <li>{@link #put} and {@link #remove} allocate replacement nodes; shared
 *       subtrees are referenced as-is</li>
 *   <li>The new root is stored in {@code root} after the operation completes</li>
 * </ul>
 *
 * <h2>Concurrency</h2>
 *
 * <ul>
 *   <li>{@code root} is {@code volatile} for safe publication of new versions</li>
 *   <li>Readers that only read {@code root} and traverse immutable nodes need no
 *       locks for snapshot consistency</li>
 *   <li>Multiple writers are not coordinated; external synchronization is required
 *       to avoid lost updates</li>
 * </ul>
 *
 * <h2>Storage abstraction</h2>
 *
 * <ul>
 *   <li>{@link KeyStorage} supplies key order and persistent vector operations</li>
 *   <li>{@link ValueStorage} supplies value vector operations aligned with keys</li>
 * </ul>
 *
 * @param <K> key type
 * @param <V> value type
 */
public class PersistentBPlusTree<K, V> implements Iterable<KeyVal<K, V>> {

    /**
     * Current tree root; {@code null} denotes an empty tree.
     *
     * <p>Writers replace this field after building a complete new version.
     * Readers typically read it once per operation so the traversal sees one
     * consistent version.</p>
     */
    private volatile Node<K, V> root;

    /**
     * Maximum keys per node; insertion splits when a node would exceed this size.
     */
    private final int maxKeys;

    /**
     * Minimum keys required in a non-root node when it must satisfy the B+ fill
     * constraint; {@code maxKeys / 2}. Used for underflow detection on delete.
     */
    private final int minKeys;

    /**
     * Factory for empty and singleton {@link KeyStorage} instances and key order.
     */
    private final KeyStorageFactory<K> ksf;

    /**
     * Creates an empty tree with the given fan-out and key storage factory.
     *
     * @param maxKeys maximum keys per node (must be consistent with
     *                {@link KeyStorage} split behavior in callers)
     * @param ksf     factory for key storage and comparison
     */
    public PersistentBPlusTree(int maxKeys, KeyStorageFactory<K> ksf) {
        this.maxKeys = maxKeys;
        this.minKeys = maxKeys / 2;
        this.ksf = ksf;
    }

    /* ==================== LOOKUP ==================== */

    /**
     * Returns the value for {@code key}, or {@code null} if the key is absent.
     *
     * <p><b>Snapshot:</b> {@link #root} is loaded into a local variable at the
     * start of the call. The lookup runs entirely on that subtree, so it reflects
     * the tree as it existed at that moment. Updates published later do not change
     * this call's result.</p>
     *
     * <p>A missing key and a key mapped to {@code null} are indistinguishable;
     * both yield {@code null}.</p>
     *
     * @param key key to look up
     * @return stored value, or {@code null} if not found or mapped to {@code null}
     */
    public V get(K key) {
        var node = root;
        if (node == null) {
            return null;
        }

        return get(node, key);
    }

    /**
     * Looks up {@code key} in the subtree rooted at {@code node}.
     *
     * <p>{@code node} is the snapshot root for this traversal (for example the
     * value {@link #get(Object)} read from {@link #root} at invocation time).
     * Descent never consults {@link #root} again.</p>
     *
     * <p>Internal nodes: {@link Search#lowerBound} on separator keys. If the bound
     * is an exact match, the child index is {@code idx + 1} so equal keys route
     * into the subtree that holds leaf entries for that separator. Leaves perform
     * the final exact match and value read.</p>
     *
     * @param node root of the subtree to search (fixed version for this lookup)
     * @param key  key to locate
     * @return value at {@code key}, or {@code null} if absent
     */
    V get(Node<K, V> node, K key) {
        return switch (node) {
            case Node.Internal<K, V>(var keys, var children) -> {
                var lb = Search.lowerBound(keys, key);
                var childIdx = lb.found() ? lb.idx() + 1 : lb.idx();
                yield get(children.child(childIdx), key);
            }

            case Node.Leaf<K, V>(var keys, var vals) -> {
                var lb = Search.lowerBound(keys, key);
                yield lb.found() ? vals.val(lb.idx()) : null;
            }
        };
    }

    /* ==================== RANGE (EAGER) ==================== */

    /**
     * Returns all entries with keys in {@code [from, to]} inclusive, in ascending
     * key order.
     *
     * <p><b>Snapshot:</b> {@link #root} is read once when this method runs. All
     * traversal uses that captured root, so the returned list reflects the tree at
     * that instant. Later writes do not alter the list. The result is an empty list
     * when the tree contains no keys in {@code [from, to]} inclusive (for example
     * when every key lies outside that range). The result is also empty when
     * {@code from} is greater than {@code to} in key order, because then no key can
     * lie in the interval.</p>
     *
     * @param from range lower bound (inclusive)
     * @param to   range upper bound (inclusive)
     * @return new list of matching entries, possibly empty
     */
    public List<KeyVal<K, V>> range(K from, K to) {
        var node = root;
        if (node == null) {
            return List.of();
        }

        var out = new ArrayList<KeyVal<K, V>>();
        range(node, from, to, out::add);
        return out;
    }

    /**
     * Same semantics as {@link #range(Object, Object)} but fills {@code out}.
     *
     * <p><b>Snapshot:</b> {@link #root} is captured once at entry; elements added
     * to {@code out} come from that version only.</p>
     *
     * <p>Nothing is added when the tree has no keys in {@code [from, to]} inclusive,
     * or when {@code from} is greater than {@code to} in key order.</p>
     *
     * @param from range lower bound (inclusive)
     * @param to   range upper bound (inclusive)
     * @param out  collection to append to (not cleared)
     * @param <T>  concrete collection type
     * @return {@code out}
     */
    <T extends Collection<KeyVal<K, V>>> T range(K from, K to, T out) {
        var node = root;
        if (node == null) {
            return out;
        }

        range(node, from, to, out::add);
        return out;
    }

    /**
     * Invokes {@code consumer} for each entry in {@code [from, to]} in ascending
     * order without allocating a list.
     *
     * <p><b>Snapshot:</b> {@link #root} is read once before traversal; callbacks
     * observe entries from that tree version only.</p>
     *
     * <p>The consumer is not invoked when the tree has no keys in {@code [from, to]}
     * inclusive, or when {@code from} is greater than {@code to} in key order.</p>
     *
     * @param from     range lower bound (inclusive)
     * @param to       range upper bound (inclusive)
     * @param consumer callback; not called when the range is empty
     */
    void range(K from, K to, Consumer<KeyVal<K, V>> consumer) {
        var node = root;
        if (node == null) {
            return;
        }

        range(node, from, to, consumer);
    }

    /**
     * Recursive range traversal on a fixed subtree.
     *
     * <p>{@code node} is the snapshot root for this range walk (the root captured
     * when the public {@code range} overload read {@link #root}). The traversal
     * does not re-read {@link #root}.</p>
     *
     * <p>Internal node: {@code start} and {@code end} are child indices that can
     * contain keys &gt;= {@code from} and &gt;= {@code to} respectively; only
     * {@code [start, end]} are visited. Leaf: slice from first key &gt;= {@code from}
     * through last key &lt;= {@code to} (lower bound on {@code to} adjusted when
     * {@code to} is not present).</p>
     *
     * @param node     subtree root for this snapshot traversal
     * @param from     inclusive lower bound
     * @param to       inclusive upper bound
     * @param consumer sink for entries
     */
    void range(Node<K, V> node, K from, K to, Consumer<KeyVal<K, V>> consumer) {
        switch (node) {
            case Node.Internal<K, V>(var keys, var children) -> {
                var start = Search.lowerBound(keys, from).idx();
                var end = Search.lowerBound(keys, to).idx();
                // Empty range in this subtree when no child index lies in [start, end].
                for (var idx = start; idx <= end; idx++) {
                    range(children.child(idx), from, to, consumer);
                }
            }
            case Node.Leaf<K, V>(var keys, var vals) -> {
                var start = Search.lowerBound(keys, from).idx();
                var endLb = Search.lowerBound(keys, to);
                // Inclusive end: if to is not in the leaf, last key <= to is idx - 1.
                var end = endLb.found() ? endLb.idx() : endLb.idx() - 1;

                for (var idx = start; idx <= end; idx++) {
                    consumer.accept(KeyVal.of(keys.key(idx), vals.val(idx)));
                }
            }
        }
    }

    /* ==================== ITERATION ==================== */

    /**
     * Iterator over all entries in ascending key order.
     *
     * <p><b>Snapshot:</b> {@link BTreeIterator#of} receives the current
     * {@link #root} once. The iterator walks only that version; it does not observe
     * structural changes published after it was created.</p>
     *
     * @return iterator bound to the tree as it was when this method returned
     */
    @Override
    public Iterator<KeyVal<K, V>> iterator() {
        return BTreeIterator.of(root);
    }

    /**
     * Lazy iterator over {@code [from, to]} inclusive in ascending order.
     *
     * <p><b>Snapshot:</b> {@link BoundedBTreeIterator#of} reads {@link #root}
     * once when this method runs. Progression uses that fixed root even if the tree
     * is updated concurrently.</p>
     *
     * <p>The iterator is empty when the tree has no keys in {@code [from, to]}
     * inclusive, or when {@code from} is greater than {@code to} in key order.</p>
     *
     * @param from inclusive lower bound
     * @param to   inclusive upper bound
     * @return bounded iterator over the snapshot at creation time
     */
    public Iterator<KeyVal<K, V>> rangeIterator(K from, K to) {
        return BoundedBTreeIterator.of(root, from, to);
    }

    /* ==================== INSERT ==================== */

    /**
     * Inserts or updates an entry and returns the previous value, if any.
     *
     * <p>Empty tree: installs a single leaf as {@link #root}. Otherwise recurses
     * from the current root; node splits propagate upward, and a root split wraps
     * the two children in a new internal node with one promoted key.</p>
     *
     * @param key key to insert or update
     * @param val value to store
     * @return previous value for {@code key}, or {@code null} if the key was absent
     */
    public V put(K key, V val) {
        var node = root;
        if (node == null) {
            root = new Node.Leaf<>(
                    ksf.single(key), ValueStorage.of(val)
            );
            return null;
        }

        var result = put(root, key, val);
        root = switch (result) {
            case PutResult.NoSplit<K, V>(var newRoot, _) -> newRoot;
            case PutResult.Split<K, V>(var left, var right, var promotedKey, _) ->
                    new Node.Internal<>(ksf.single(promotedKey), Children.of(left, right));
        };

        return result.replaced();
    }

    /**
     * Recursive insert starting at {@code node}; used by {@link #put} and for
     * testing or alternate entry points.
     *
     * @param node subtree root
     * @param key  key to insert
     * @param val  value to store
     * @return outcome of the insertion at this subtree
     */
    PutResult<K, V> put(Node<K, V> node, K key, V val) {
        return switch (node) {
            case Node.Internal<K, V>(var keys, var children) -> putInternal(keys, children, key, val);
            case Node.Leaf<K, V>(var keys, var vals) -> putLeaf(keys, vals, key, val);
        };
    }

    /**
     * Descends to the child chosen by {@link Search#lowerBound}, applies the child
     * {@link #put(Node, Object, Object)} result, then either replaces one child,
     * absorbs a split into this node, or splits this internal node.
     *
     * @param keys     separator keys of this internal node
     * @param children child pointers aligned with {@code keys}
     * @param key      key to insert
     * @param val      value to insert
     * @return updated subtree description for the parent
     */
    private PutResult<K, V> putInternal(KeyStorage<K> keys, Children<K, V> children, K key, V val) {
        var lb = Search.lowerBound(keys, key);
        var childIdx = lb.found() ? lb.idx() + 1 : lb.idx();
        var child = children.child(childIdx);

        return switch (put(child, key, val)) {
            case PutResult.NoSplit<K, V>(var node, var replaced) -> new PutResult.NoSplit<>(
                    new Node.Internal<>(keys, children.replace(childIdx, node)),
                    replaced
            );
            case PutResult.Split<K, V>(var left, var right, var promotedKey, var replaced) -> {
                if (keys.size() < maxKeys) {
                    yield new PutResult.NoSplit<>(new Node.Internal<>(
                            keys.insert(childIdx, promotedKey),
                            children.insert(childIdx, left, right)
                    ), replaced);
                }
                var keySplit = keys.insertAndSplit(childIdx, minKeys, promotedKey);
                var childrenSplit = children.insertAndSplit(childIdx, minKeys, left, right);
                yield new PutResult.Split<>(
                        new Node.Internal<>(keySplit.left(), childrenSplit.left()),
                        new Node.Internal<>(keySplit.right(), childrenSplit.right()),
                        keySplit.promotedKey(),
                        replaced
                );

            }
        };
    }

    /**
     * Leaf-level insert: replace if key exists; otherwise insert, splitting if full.
     *
     * @param keys leaf keys
     * @param vals parallel values
     * @param key  key to insert
     * @param val  value to insert
     * @return outcome including replaced value for updates
     */
    private PutResult<K, V> putLeaf(KeyStorage<K> keys, ValueStorage<V> vals, K key, V val) {
        var lb = Search.lowerBound(keys, key);
        if (lb.found()) {
            var replaced = vals.val(lb.idx());
            return new PutResult.NoSplit<>(
                    new Node.Leaf<>(keys, vals.replace(lb.idx(), val)),
                    replaced
            );
        }

        if (keys.size() < maxKeys) {
            return new PutResult.NoSplit<>(new Node.Leaf<>(
                    keys.insert(lb.idx(), key),
                    vals.insert(lb.idx(), val)
            ), null);
        } else {
            var keySplit = keys.insertAndSplit(lb.idx(), minKeys, key);
            var valSplit = vals.insertAndSplit(lb.idx(), minKeys, val);

            var left = new Node.Leaf<>(keySplit.left(), valSplit.left());
            var right = new Node.Leaf<>(keySplit.right(), valSplit.right());

            return new PutResult.Split<>(
                    left,
                    right,
                    keySplit.promotedKey(),
                    null
            );
        }
    }

    /* ==================== DELETE ==================== */

    /**
     * Removes {@code key} and returns the value previously stored, or {@code null}
     * if the key was absent.
     *
     * <p>Loads {@link #root} once, runs {@link #remove(Node, Object)}, then
     * publishes a new root according to {@link DeleteResult}. {@code NotFound}
     * leaves {@code root} unchanged.</p>
     *
     * <p>After a {@link DeleteResult.Shrink}, the replacement root may still be
     * structurally redundant at the top. Normalization reduces height or empties
     * the tree:</p>
     *
     * <pre>
     *  Branch root with zero separator keys (only one child pointer left):
     *
     *      keys:     [ ]
     *      children: [ C ]
     *      =&gt; root becomes C (height drops by one)
     *
     *  Leaf root with zero keys (last entry removed):
     *
     *      keys: [ ]
     *      =&gt; root becomes null (tree empty)
     * </pre>
     *
     * @param key key to remove
     * @return removed value, or {@code null} if not found
     */
    public V remove(K key) {
        var node = root;
        if (node == null) {
            return null;
        }
        var result = remove(node, key);
        root = switch (result) {
            case DeleteResult.NotFound<K, V> _ -> node;
            case DeleteResult.NoShrink<K, V>(var newRoot, _) -> newRoot;
            case DeleteResult.Shrink<K, V>(var newRoot, _) -> switch (newRoot) {
                case Node.Internal<K, V>(var keys, var children)
                        when keys.size() == 0 -> children.child(0);
                case Node.Leaf<K, V>(var keys, _)
                        when keys.size() == 0 -> null;
                default -> newRoot;
            };
        };
        return result.removed();
    }

    /**
     * Dispatches deletion by node shape: {@link Node.Internal} delegates to
     * {@link #removeInternal}, {@link Node.Leaf} to {@link #removeLeaf}.
     *
     * @param node subtree root
     * @param key  key to remove
     * @return {@link DeleteResult.NotFound}, {@link DeleteResult.NoShrink}, or
     *         {@link DeleteResult.Shrink} for ancestors to absorb
     */
    public DeleteResult<K, V> remove(Node<K, V> node, K key) {
        return switch (node) {
            case Node.Internal<K, V>(var keys, var children) -> removeInternal(keys, children, key);
            case Node.Leaf<K, V>(var keys, var vals) -> removeLeaf(keys, vals, key);
        };
    }

    /**
     * Recurses into one child (same index rule as lookup: exact match on a
     * separator goes to the right child), then repairs the subtree if the child
     * reports {@link DeleteResult.Shrink}.
     *
     * <h3>Child result handling</h3>
     * <pre>
     *  NotFound     -&gt; bubble up unchanged (nothing to splice)
     *  NoShrink     -&gt; replace children[idx] with the updated child node only
     *  Shrink       -&gt; child violated min fill; try rebalance at this level
     * </pre>
     *
     * <h3>Shrink repair order (fixed)</h3>
     * <ol>
     *   <li>Left borrow: sibling at {@code idx - 1} has {@code keys.size() &gt; minKeys}</li>
     *   <li>Right borrow: sibling at {@code idx + 1} has spare keys</li>
     *   <li>Merge: combine borrower with a neighbor; parent loses one separator</li>
     * </ol>
     *
     * <p>After merge, the updated parent node may itself underflow; the method
     * returns {@code Shrink} again so the grandparent can run the same logic.</p>
     *
     * @param keys     separator keys of this branch node
     * @param children child pointers aligned with {@code keys}
     * @param key      key to delete from the subtree
     * @return result to propagate upward
     */
    private DeleteResult<K, V> removeInternal(KeyStorage<K> keys, Children<K, V> children, K key) {
        var lb = Search.lowerBound(keys, key);
        var idx = lb.found() ? lb.idx() + 1 : lb.idx();

        return switch (remove(children.child(idx), key)) {
            case DeleteResult.NotFound<K, V> nf -> nf;

            case DeleteResult.NoShrink<K, V>(var node, V removed) -> new DeleteResult.NoShrink<>(
                    new Node.Internal<>(keys, children.replace(idx, node)), removed
            );

            case DeleteResult.Shrink<K, V>(var node, V removed) -> {
                if (idx > 0 && canBorrow(children.child(idx - 1))) {
                    var donor = children.child(idx - 1);
                    var newNode = switch (donor) {
                        case Node.Leaf<K, V> leafDonor ->
                                borrowFromLeftSibling((Node.Leaf<K, V>) node, leafDonor, keys, children, idx - 1);
                        case Node.Internal<K, V> internalDonor ->
                                borrowFromLeftSibling((Node.Internal<K, V>) node, internalDonor, keys, children, idx - 1);
                    };

                    yield new DeleteResult.NoShrink<>(newNode, removed);
                }

                if (idx < children.size() - 1 && canBorrow(children.child(idx + 1))) {
                    var donor = children.child(idx + 1);
                    var newNode = switch (donor) {
                        case Node.Leaf<K, V> leafDonor ->
                                borrowFromRightSibling((Node.Leaf<K, V>) node, leafDonor, keys, children, idx);
                        case Node.Internal<K, V> internalDonor ->
                                borrowFromRightSibling((Node.Internal<K, V>) node, internalDonor, keys, children, idx);
                    };

                    yield new DeleteResult.NoShrink<>(newNode, removed);
                }

                var newNode = switch (node) {
                    case Node.Leaf<K, V> rightLeaf when idx > 0 -> merge(
                            (Node.Leaf<K, V>) children.child(idx - 1),
                            rightLeaf,
                            keys,
                            children,
                            idx - 1
                    );
                    case Node.Leaf<K, V> leftLeaf -> merge(
                            leftLeaf,
                            (Node.Leaf<K, V>) children.child(idx + 1),
                            keys,
                            children,
                            idx
                    );
                    case Node.Internal<K, V> rightInternal when idx > 0 -> merge(
                            (Node.Internal<K, V>) children.child(idx - 1),
                            rightInternal,
                            keys,
                            children,
                            idx - 1
                    );
                    case Node.Internal<K, V> leftInternal -> merge(
                            leftInternal,
                            (Node.Internal<K, V>) children.child(idx + 1),
                            keys,
                            children,
                            idx
                    );
                };

                yield underflows(newNode.keys())
                        ? new DeleteResult.Shrink<>(newNode, removed)
                        : new DeleteResult.NoShrink<>(newNode, removed);

            }
        };
    }

    /**
     * Merges two adjacent branch children when neither side can lend a key.
     *
     * <p>The parent separator at {@code parentIdx} sits between the two nodes in
     * key order; it is not duplicated in either child. Merge forms one wider branch
     * node by concatenating: all keys and children from {@code left}, then
     * {@code keys.key(parentIdx)}, then all keys and children from {@code right}.</p>
     *
     * <pre>
     *  Before (parent view):
     *      ... | Kp | ...
     *           /   \
     *         left  right
     *
     *  After:
     *      merged branch node = left.keys ++ [Kp] ++ right.keys
     *      merged children    = left.children ++ right.children
     *      parent drops Kp and one child pointer (two slots -&gt; one)
     * </pre>
     *
     * @param left       left branch child (smaller keys)
     * @param right      right branch child
     * @param keys       parent separator keys
     * @param children   parent child list (includes both nodes)
     * @param parentIdx  index of {@code Kp} between {@code left} and {@code right}
     * @return new parent branch node with one fewer separator and one fewer child
     */
    private Node.Internal<K, V> merge(
            Node.Internal<K, V> left,
            Node.Internal<K, V> right,
            KeyStorage<K> keys,
            Children<K, V> children,
            int parentIdx
    ) {
        var leftKeys = left.keys();
        var leftChildren = left.children();

        var rightKeys = right.keys();
        var rightChildren = right.children();

        var separatorKey = keys.key(parentIdx);

        var mergedKeys = leftKeys.insertAndMerge(leftKeys.size(), separatorKey, rightKeys);
        var mergedChildren = leftChildren.merge(rightChildren);
        var mergedNode = new Node.Internal<>(mergedKeys, mergedChildren);

        var newKeys = keys.remove(parentIdx);
        var newChildren = children.removeAndReplace(parentIdx, mergedNode);

        return new Node.Internal<>(newKeys, newChildren);
    }

    /**
     * Merges two adjacent leaf children: concatenates key/value pairs in order and
     * removes the parent separator between them (leaves do not store that key).
     *
     * <pre>
     *  left leaf:  [ ... ]
     *  right leaf: [ ... ]     (all keys &gt; left's keys by invariant)
     *
     *  merged leaf = left.keys ++ right.keys (values merged in parallel)
     *  parent loses keys[parentIdx] and one redundant child slot
     * </pre>
     *
     * @param left       left leaf node
     * @param right      right leaf node
     * @param keys       parent separator keys
     * @param children   parent child list
     * @param parentIdx  separator index between these two leaves
     * @return new parent branch node referencing the single merged leaf
     */
    private Node.Internal<K, V> merge(
            Node.Leaf<K, V> left,
            Node.Leaf<K, V> right,
            KeyStorage<K> keys,
            Children<K, V> children,
            int parentIdx
    ) {
        var leftKeys = left.keys();
        var leftVals = left.values();

        var rightKeys = right.keys();
        var rightVals = right.values();

        var mergedKeys = leftKeys.merge(rightKeys);
        var mergedValues = leftVals.merge(rightVals);
        var mergedNode = new Node.Leaf<>(mergedKeys, mergedValues);

        var newKeys = keys.remove(parentIdx);
        var newChildren = children.removeAndReplace(parentIdx, mergedNode);

        return new Node.Internal<>(newKeys, newChildren);
    }

    /**
     * Redistributes from the left sibling branch node into the underflowing right
     * sibling branch node (same parent). One rotation moves one separator and one
     * subtree across {@code keys[parentIdx]}.
     *
     * <pre>
     *  Parent:   ... | Kp | ...
     *               /     \
     *            donor      borrower   (donor.keys.size() &gt; minKeys)
     *
     *  Step:
     *    promoted = donor's last separator (removed from donor)
     *    borrowed = donor's last rightmost child (removed from donor)
     *    Kp is inserted before borrower's first separator; borrowed child is
     *    linked as borrower's new leftmost child
     *    parent replaces Kp with promoted
     *
     *  After: donor is shorter, borrower regains enough keys; parent still valid.
     * </pre>
     *
     * @param borrower  right child node below {@code Kp} (underflow after delete)
     * @param donor       left child node of same parent (must have spare keys)
     * @param keys        parent separator keys
     * @param children    parent child list
     * @param parentIdx   index of {@code Kp} between {@code donor} and {@code borrower}
     * @return rebuilt parent branch node with updated children
     */
    private Node.Internal<K, V> borrowFromLeftSibling(
            Node.Internal<K, V> borrower,
            Node.Internal<K, V> donor,
            KeyStorage<K> keys,
            Children<K, V> children,
            int parentIdx
    ) {

        var donorKeys = donor.keys();
        var donorChildren = donor.children();

        var borrowerKeys = borrower.keys();
        var borrowerChildren = borrower.children();

        var depromotedKey = keys.key(parentIdx);

        var last = donorKeys.size() - 1;
        var promotedKey = donorKeys.key(last);
        var borrowedChild = donorChildren.child(last + 1);

        var leftKeys = donorKeys.remove(last);
        var leftChildren = donorChildren.remove(last + 1);
        var leftNode = new Node.Internal<>(leftKeys, leftChildren);

        var rightKeys = borrowerKeys.insert(0, depromotedKey);
        var rightChildren = borrowerChildren.insert(0, borrowedChild);
        var rightNode = new Node.Internal<>(rightKeys, rightChildren);

        var newKeys = keys.replace(parentIdx, promotedKey);
        var newChildren = children.replace(parentIdx, leftNode, rightNode);

        return new Node.Internal<>(newKeys, newChildren);
    }

    /**
     * Mirror of left-branch borrow for {@link Node.Internal} nodes: shifts the
     * smallest separator and leftmost
     * child from the right donor into the end of the left borrower; parent key at
     * {@code parentIdx} is replaced by the donor's former smallest separator.
     *
     * <pre>
     *  Parent:   ... | Kp | ...
     *               /     \
     *          borrower   donor     ((donor.keys.size() &gt; minKeys)
     *
     *  promoted = donor's first separator (removed)
     *  borrowed = donor's first child (removed)
     *  Kp and borrowed append to borrower; Kp in parent becomes promoted
     * </pre>
     *
     * @param borrower  left child node below {@code Kp} (underflow)
     * @param donor       right sibling node with spare keys
     * @param keys        parent separator keys
     * @param children    parent child list
     * @param parentIdx   index of {@code Kp} between {@code borrower} and {@code donor}
     * @return rebuilt parent branch node
     */
    private Node.Internal<K, V> borrowFromRightSibling(
            Node.Internal<K, V> borrower,
            Node.Internal<K, V> donor,
            KeyStorage<K> keys,
            Children<K, V> children,
            int parentIdx
    ) {

        var donorKeys = donor.keys();
        var donorChildren = donor.children();

        var borrowerKeys = borrower.keys();
        var borrowerChildren = borrower.children();

        var depromotedKey = keys.key(parentIdx);

        var promotedKey = donorKeys.key(0);
        var borrowedChild = donorChildren.child(0);

        var rightKeys = donorKeys.remove(0);
        var rightChildren = donorChildren.remove(0);
        var rightNode = new Node.Internal<>(rightKeys, rightChildren);

        var leftKeys = borrowerKeys.insert(borrowerKeys.size(), depromotedKey);
        var leftChildren = borrowerChildren.insert(borrowerChildren.size(), borrowedChild);
        var leftNode = new Node.Internal<>(leftKeys, leftChildren);

        var newKeys = keys.replace(parentIdx, promotedKey);
        var newChildren = children.replace(parentIdx, leftNode, rightNode);

        return new Node.Internal<>(newKeys, newChildren);
    }

    /**
     * Redistributes one entry from the left sibling leaf into the right sibling
     * leaf (the underflowing child). Used when the right leaf has
     * {@code keys.size() &lt; minKeys} and the left leaf can donate:
     * {@code donor.keys.size() &gt; minKeys} (see {@link #canBorrow}).
     *
     * <p>Invariants: all keys in {@code donor} are strictly less than all keys in
     * {@code borrower} (sibling leaves in key order). The parent separator at
     * {@code parentIdx} is the smallest key that could appear in the right leaf;
     * after the move it is replaced with {@code rightKeys.key(0)} (new minimum of
     * the right leaf).</p>
     *
     * <p>Copy-on-write: builds two new {@link Node.Leaf} instances and a new
     * parent {@link Node.Internal}; input nodes are not mutated.</p>
     *
     * <p>Example with {@code minKeys == 2}: right leaf underflows with one key;
     * left leaf has three keys so it can lose one and still have two.</p>
     *
     * <pre>
     *  donor (left)     borrower (right)
     *  [ 10, 20, 30 ]   [ 40 ]        | one key only: size 1 &lt; minKeys (2) = underflow
     *
     *  Move last entry (30) from donor to front of borrower:
     *  [ 10, 20 ]       [ 30, 40 ]   | both have &gt;= minKeys
     *
     *  Parent separator at parentIdx := 30 (= new first key of right leaf)
     * </pre>
     *
     * @param borrower  right leaf node (fewer than {@code minKeys} keys)
     * @param donor       left sibling leaf ({@code keys.size() &gt; minKeys})
     * @param keys        parent separator keys
     * @param children    parent child list
     * @param parentIdx   separator index between {@code donor} and {@code borrower}
     * @return new parent branch node (new key storage, new child list)
     */
    private Node.Internal<K, V> borrowFromLeftSibling(
            Node.Leaf<K, V> borrower,
            Node.Leaf<K, V> donor,
            KeyStorage<K> keys,
            Children<K, V> children,
            int parentIdx
    ) {

        var donorKeys = donor.keys();
        var donorVals = donor.values();

        var last = donorKeys.size() - 1;
        var borrowedKey = donorKeys.key(last);
        var borrowedVal = donorVals.val(last);

        var leftKeys = donorKeys.remove(last);
        var leftVals = donorVals.remove(last);
        var leftNode = new Node.Leaf<>(leftKeys, leftVals);

        var rightKeys = borrower.keys().insert(0, borrowedKey);
        var rightVals = borrower.values().insert(0, borrowedVal);
        var rightNode = new Node.Leaf<>(rightKeys, rightVals);

        var newKeys = keys.replace(parentIdx, rightKeys.key(0));
        var newChildren = children.replace(parentIdx, leftNode, rightNode);

        return new Node.Internal<>(newKeys, newChildren);
    }

    /**
     * Redistributes one entry from the right sibling leaf into the left sibling
     * leaf (the underflowing child). Symmetric to the leaf overload of
     * {@code borrowFromLeftSibling} (donor on the left, borrower on the right):
     * the left leaf is too small; the right leaf must satisfy
     * {@code donor.keys.size() &gt; minKeys} so it can give one entry and remain
     * valid.
     *
     * <p>Invariants: every key in {@code borrower} is strictly less than every key
     * in {@code donor}. The implementation removes {@code donor}'s minimum entry
     * and appends it to {@code borrower}'s maximum side, preserving sorted order.
     * The parent separator is updated to the new minimum key of the right leaf
     * ({@code rightKeys.key(0)} after the removal).</p>
     *
     * <p>Copy-on-write: new left leaf, new right leaf, new parent branch node.</p>
     *
     * <p>Example with {@code minKeys == 2}:</p>
     *
     * <pre>
     *  borrower (left)   donor (right)
     *  [ 10 ]            [ 20, 30, 40 ]   | left has 1 &lt; minKeys: underflow
     *
     *  Move first entry (20) from donor to end of borrower:
     *  [ 10, 20 ]        [ 30, 40 ]       | both &gt;= minKeys
     *
     *  Parent separator at parentIdx := 30 (= new first key of right leaf)
     * </pre>
     *
     * @param borrower  left leaf node ({@code keys.size() &lt; minKeys})
     * @param donor       right sibling leaf ({@code keys.size() &gt; minKeys})
     * @param keys        parent separator keys
     * @param children    parent child list
     * @param parentIdx   separator index between {@code borrower} and {@code donor}
     * @return new parent branch node (updated separators and child pointers)
     */
    private Node.Internal<K, V> borrowFromRightSibling(
            Node.Leaf<K, V> borrower,
            Node.Leaf<K, V> donor,
            KeyStorage<K> keys,
            Children<K, V> children,
            int parentIdx
    ) {

        var donorKeys = donor.keys();
        var donorVals = donor.values();

        var borrowedKey = donorKeys.key(0);
        var borrowedVal = donorVals.val(0);

        var rightKeys = donorKeys.remove(0);
        var rightVals = donorVals.remove(0);
        var rightNode = new Node.Leaf<>(rightKeys, rightVals);

        var leftKeys = borrower.keys()
                .insert(borrower.keys().size(), borrowedKey);
        var leftVals = borrower.values()
                .insert(borrower.values().size(), borrowedVal);
        var leftNode = new Node.Leaf<>(leftKeys, leftVals);

        var newKeys = keys.replace(parentIdx, rightKeys.key(0));
        var newChildren = children.replace(parentIdx, leftNode, rightNode);

        return new Node.Internal<>(newKeys, newChildren);
    }

    /**
     * Tests whether a sibling can lose one separator and still satisfy
     * {@code keys.size() &gt;= minKeys} (strictly greater than {@code minKeys} so
     * after one key moves out the node remains valid).
     *
     * @param node candidate donor sibling
     * @return {@code true} if borrow is allowed before merge
     */
    private boolean canBorrow(Node<K, V> node) {
        return node.keys().size() > minKeys;
    }

    /**
     * Whether a node's key count is below the minimum allowed for a non-root node
     * after a deletion (root may temporarily be smaller; {@link #remove(K)}
     * normalizes).
     *
     * @param keys key storage to check
     * @return {@code true} if {@code keys.size() &lt; minKeys}
     */
    private boolean underflows(KeyStorage<K> keys) {
        return keys.size() < minKeys;
    }

    /**
     * Deletes at the only level that stores values. Builds a new leaf without the
     * entry; does not touch ancestors directly. Returns {@link DeleteResult.Shrink}
     * when the new key count falls under {@link #minKeys} so
     * {@link #removeInternal} can rebalance.
     *
     * @param keys leaf key column
     * @param vals parallel value column
     * @param key  key to remove
     * @return {@link DeleteResult.NotFound} if absent; else shrink or no-shrink
     */
    private DeleteResult<K, V> removeLeaf(KeyStorage<K> keys, ValueStorage<V> vals, K key) {
        var lb = Search.lowerBound(keys, key);

        if (!lb.found()) {
            return new DeleteResult.NotFound<>();
        }

        var idx = lb.idx();
        var removed = vals.val(idx);
        var newKeys = keys.remove(idx);
        var newVals = vals.remove(idx);
        var newLeaf = new Node.Leaf<>(newKeys, newVals);

        return underflows(newKeys)
                ? new DeleteResult.Shrink<>(newLeaf, removed)
                : new DeleteResult.NoShrink<>(newLeaf, removed);
    }
}

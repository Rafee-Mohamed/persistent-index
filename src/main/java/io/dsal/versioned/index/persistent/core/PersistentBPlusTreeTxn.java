package io.dsal.versioned.index.persistent.core;

import io.dsal.versioned.index.api.Direction;
import io.dsal.versioned.index.api.Range;
import io.dsal.versioned.index.api.Snapshot;
import io.dsal.versioned.index.api.Txn;
import io.dsal.versioned.index.persistent.layout.ValueStorage;
import io.dsal.versioned.index.persistent.layout.KeyStorageFactory;
import io.dsal.versioned.index.persistent.layout.KeyStorage;
import io.dsal.versioned.index.persistent.util.Search;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

/**
 * {@link Txn} implementation for {@link io.dsal.versioned.index.persistent.PersistentBPlusTree}.
 *
 * <p>A transaction captures the latest committed state from {@link StateCommitter}
 * at creation time and builds all mutations into a private {@link UncommittedState}
 * working tree. Reads through the {@link io.dsal.versioned.index.api.TxnHandle} see
 * the working state, providing read-your-own-writes semantics. A separate
 * {@link Snapshot} over the pre-transaction committed state is also captured and
 * exposed via {@link #snapshot()}; it does not reflect in-transaction writes.
 *
 * <h2>Atomicity</h2>
 *
 * <p>Mutations accumulate in the working root without touching the committed root.
 * {@link #commit()} publishes the working root to {@link StateCommitter} in a
 * single volatile write, making all changes visible atomically to readers that
 * acquire a new snapshot or transaction after the commit. Readers that already
 * hold a prior snapshot or transaction are unaffected.
 *
 * <pre>
 *   Committed root (immutable) --+-- readers see stable snapshot
 *                                |
 *   Working root (this txn)      |-- mutations accumulate here
 *                                |
 *   commit() --&gt; volatile write  --+-- new committed root visible to all
 * </pre>
 *
 * <h2>Copy-on-write and exclusive-node optimization</h2>
 *
 * <p>Every node copied from the committed tree is added to {@link #exclusive}.
 * When a mutation touches a node already in {@code exclusive}, {@link Node.Internal#mutate}
 * or {@link Node.Leaf#mutate} updates it in place instead of allocating another
 * copy. This means each shared node is copied at most once per transaction,
 * regardless of how many operations touch the same path.
 *
 * <pre>
 *   First touch of a committed node:  copy node, add to exclusive
 *   Later touch of the same node:     update in place (already in exclusive)
 * </pre>
 *
 * <p>Nodes created fresh within the transaction (new leaves on insert, split
 * results) are also added to {@code exclusive} immediately so the same rule
 * applies if they are revisited during rebalancing.
 *
 * <h2>Structural sharing</h2>
 *
 * <p>Only nodes on the root-to-leaf path affected by a mutation are copied.
 * Unchanged subtrees are referenced as-is from the working root, sharing
 * structure with the committed tree.
 *
 * <p>This type is mutable and not thread-safe. After {@link #commit()} all
 * methods except {@link #committed()} throw {@link IllegalStateException}.
 *
 * @param <K> key type
 * @param <V> value type
 */
public class PersistentBPlusTreeTxn<K, V> implements Txn<K, V> {

    /** Publishes the new committed state on {@link #commit()}. */
    private final StateCommitter<K, V> committer;
    /** Private mutable working state for this transaction. */
    private final UncommittedState<K, V> us;
    /** Factory for key storage instances used when creating new nodes. */
    private final KeyStorageFactory<K> ksf;
    /** Committed snapshot captured at the start of this transaction. */
    private final Snapshot<K, V> snapshot;
    /** Read engine shared with snapshots; used for all read operations. */
    private final ReadQuery<K, V> query;
    /**
     * Nodes that belong exclusively to this transaction. A node is exclusive if
     * it was freshly created during this transaction (split result, new leaf) or
     * has already been copied once from the committed tree. Exclusive nodes may
     * be mutated in place by {@link Node.Internal#mutate} and {@link Node.Leaf#mutate},
     * ensuring each shared node is copied at most once per transaction.
     */
    private final Set<Node<K, V>> exclusive;

    /** Maximum number of keys per node before a split is required. */
    private final int maxKeys;
    /**
     * Minimum number of keys required in a non-root node; {@code maxKeys / 2}.
     * A node with fewer keys after deletion has underflowed and triggers
     * borrow or merge at its parent.
     */
    private final int minKeys;

    /** {@code true} after {@link #commit()} has been called. */
    private boolean committed;

    /**
     * Creates a transaction from the latest committed state.
     *
     * @param committer committed-state publisher used on commit
     * @param ksf key-storage factory used for newly created key runs
     * @param query read engine for point/range reads and iteration
     * @param maxKeys maximum keys per node before split
     * @param minKeys minimum keys per non-root node before underflow handling
     */
    public PersistentBPlusTreeTxn(
            StateCommitter<K, V> committer,
            KeyStorageFactory<K> ksf,
            ReadQuery<K, V> query,
            int maxKeys,
            int minKeys
    ) {
        this.us = new UncommittedState<>(committer.committed());
        this.snapshot = new PersistentBPlusTreeSnapshot<>(committer.committed(), query);
        this.committer = committer;
        this.ksf = ksf;
        this.query = query;
        this.maxKeys = maxKeys;
        this.minKeys = minKeys;
        this.exclusive = new HashSet<>();
        this.committed = false;
    }

    /**
     * Throws if this transaction is already committed.
     *
     * @throws IllegalStateException if commit was already executed
     */
    public void throwIfCommitted() {
        if (committed) {
            throw new IllegalStateException("Txn use after committed");
        }
    }

    /**
     * Inserts or updates one key in the transaction state.
     *
     * @param key key to write
     * @param value value to associate with {@code key}
     * @return previous value for {@code key}, if present
     * @throws IllegalStateException if the transaction is already committed
     */
    @Override
    public Optional<V> put(K key, V value) {
        throwIfCommitted();
        if (us.isEmpty()) {
            us.increment();
            us.setRoot(new Node.Leaf<>(ksf.single(key), ValueStorage.of(value)));
            exclusive.add(us.root());
            return Optional.empty();
        }

        var result = put(us.root(), key, value);
        var newRoot = switch (result) {
            case PutResult.NoSplit<K, V>(var node, _) -> node;
            case PutResult.Split<K, V>(var left, var right, var promotedKey) ->
                    new Node.Internal<>(ksf.single(promotedKey), Children.of(left, right));
        };

        exclusive.add(newRoot);
        us.setRoot(newRoot);

        if (result.replaced() == null) {
            us.increment();
        }

        return Optional.ofNullable(result.replaced());
    }



    /**
     * Routes insertion to the appropriate node type.
     *
     * @param node subtree root to insert into
     * @param key  key to insert
     * @param val  value to associate with {@code key}
     * @return result indicating whether the subtree split and carrying any prior value
     */
    private PutResult<K, V> put(Node<K, V> node, K key, V val) {
        return switch (node) {
            case Node.Internal<K, V> internal -> putInternal(internal, key, val);
            case Node.Leaf<K, V> leaf -> putLeaf(leaf, key, val);
        };
    }

    /**
     * Descends to the child covering {@code key} via upper-bound search on separator
     * keys, applies the recursive result, then either replaces one child pointer,
     * absorbs a child split, or splits this internal node.
     *
     * <h3>Child result handling</h3>
     * <pre>
     *  NoSplit (child == newNode) -&gt; child unchanged; this node returned as-is (no copy)
     *  NoSplit (child != newNode) -&gt; replace children[childIdx] via exclusive mutate
     *  Split                      -&gt; absorb if room; otherwise split this node too
     * </pre>
     *
     * @param node internal node receiving the insertion
     * @param key  key to insert
     * @param val  value to associate with {@code key}
     * @return updated subtree description for the parent
     */
    private PutResult<K, V> putInternal(Node.Internal<K, V> node, K key, V val) {
        var keys = node.keys();
        var children = node.children();

        var childIdx = Search.upperBound(node.keys(), key);
        var child = children.child(childIdx);

        return switch (put(child, key, val)) {
            case PutResult.NoSplit<K, V>(var newNode, var replaced) when child == newNode -> new PutResult.NoSplit<>(
                    node,
                    replaced
            );
            case PutResult.NoSplit<K, V>(var newNode, var replaced) -> new PutResult.NoSplit<>(
                    node.mutate(keys, children.replace(childIdx, newNode), exclusive),
                    replaced
            );
            case PutResult.Split<K, V>(var left, var right, var promotedKey) -> {
                if (keys.size() < maxKeys) {
                    var newNode = node.mutate(keys.insert(childIdx, promotedKey), children.insert(childIdx, left, right), exclusive);
                    yield new PutResult.NoSplit<>(newNode, null);
                }
                var keySplit = keys.insertAndSplitAround(childIdx, minKeys, promotedKey);
                var childrenSplit = children.insertAndSplit(childIdx, minKeys + 1, left, right);

                var leftNewNode = new Node.Internal<>(keySplit.left(), childrenSplit.left());
                var rightNewNode = new Node.Internal<>(keySplit.right(), childrenSplit.right());

                exclusive.add(leftNewNode);
                exclusive.add(rightNewNode);

                yield new PutResult.Split<>(leftNewNode, rightNewNode, keySplit.promotedKey());
            }
        };
    }

    /**
     * Leaf-level insert: replace value if key exists; otherwise insert in sorted
     * position, splitting into two leaves when the node is full.
     *
     * @param leaf leaf node receiving the insertion
     * @param key  key to insert or update
     * @param val  value to store
     * @return outcome including the prior value on update, or {@code null} on insert
     */
    private PutResult<K, V> putLeaf(Node.Leaf<K, V> leaf, K key, V val) {
        var keys = leaf.keys();
        var vals = leaf.values();
        var lb = Search.findAndLowerBound(keys, key);

        if (lb.found()) {
            var replaced = vals.val(lb.idx());
            return new PutResult.NoSplit<>(
                    leaf.mutate(keys, vals.replace(lb.idx(), val), exclusive),
                    replaced
            );
        }

        if (keys.size() < maxKeys) {
            return new PutResult.NoSplit<>(leaf.mutate(
                    keys.insert(lb.idx(), key),
                    vals.insert(lb.idx(), val),
                    exclusive
            ), null);
        } else {
            var keySplit = keys.insertAndSplit(lb.idx(), minKeys, key);
            var valSplit = vals.insertAndSplit(lb.idx(), minKeys, val);

            var left = new Node.Leaf<>(keySplit.left(), valSplit.left());
            var right = new Node.Leaf<>(keySplit.right(), valSplit.right());

            exclusive.add(left);
            exclusive.add(right);

            return new PutResult.Split<>(
                    left,
                    right,
                    keySplit.promotedKey()
            );
        }
    }

    /**
     * Removes one key from the transaction state.
     *
     * <p>After a {@link DeleteResult.Shrink} propagates to the root, the root
     * is normalized:
     * <pre>
     *  Internal root with zero separator keys (one child left):
     *      =&gt; root becomes that single child (height drops by one)
     *
     *  Leaf root with zero keys (last entry removed):
     *      =&gt; root becomes null (tree is empty)
     * </pre>
     *
     * @param key key to remove
     * @return removed value, if present
     * @throws IllegalStateException if the transaction is already committed
     */
    @Override
    public Optional<V> remove(K key) {
        throwIfCommitted();
        if (us.isEmpty()) {
            return Optional.empty();
        }

        var result = remove(us.root(), key);
        var newRoot = switch (result) {
            case DeleteResult.NotFound<K, V> _ -> us.root();
            case DeleteResult.NoShrink<K, V>(var node, _) -> node;
            case DeleteResult.Shrink<K, V>(var node, _) -> switch (node) {
                case Node.Internal<K, V> internal
                        when internal.keys().size() == 0 -> internal.children().child(0);
                case Node.Leaf<K, V> leaf
                        when leaf.keys().size() == 0 -> null;
                default -> node;
            };
        };

        if (newRoot != null) {
            exclusive.add(newRoot);
        }

        us.setRoot(newRoot);

        if (result.removed() != null) {
            us.decrement();
        }

        return Optional.ofNullable(result.removed());
    }

    /**
     * Routes deletion to the appropriate node type.
     *
     * @param node subtree root to remove from
     * @param key  key to remove
     * @return result indicating whether the key was found, the subtree shrank, and the removed value
     */
    public DeleteResult<K, V> remove(Node<K, V> node, K key) {
        return switch (node) {
            case Node.Internal<K, V> internal -> removeInternal(internal, key);
            case Node.Leaf<K, V> leaf -> removeLeaf(leaf, key);
        };
    }

    /**
     * Recurses into one child (upper-bound index {@code idx}), then repairs the
     * subtree if the child reports {@link DeleteResult.Shrink}.
     *
     * <h3>Child result handling</h3>
     * <pre>
     *  NotFound               -&gt; bubble up unchanged (nothing to splice)
     *  NoShrink (same child)  -&gt; this node returned as-is (no copy)
     *  NoShrink (new child)   -&gt; replace children[idx] with the updated child only
     *  Shrink                 -&gt; child violated min fill; try rebalance at this level
     * </pre>
     *
     * <h3>Shrink repair order (fixed)</h3>
     * <ol>
     *   <li>Left borrow: sibling at {@code idx - 1} has {@code keys.size() &gt; minKeys}</li>
     *   <li>Right borrow: sibling at {@code idx + 1} has spare keys</li>
     *   <li>Merge: combine child with a neighbor; parent loses one separator</li>
     * </ol>
     *
     * <p>After merge, the updated parent may itself underflow; the method returns
     * {@link DeleteResult.Shrink} again so the grandparent can run the same logic.</p>
     *
     * @param node internal node receiving the deletion
     * @param key  key to remove
     * @return result to propagate upward
     */
    private DeleteResult<K, V> removeInternal(Node.Internal<K, V> node, K key) {
        var keys = node.keys();
        var children = node.children();

        var idx = Search.upperBound(keys, key);
        var child = children.child(idx);

        return switch (remove(child, key)) {
            case DeleteResult.NotFound<K, V> nf -> nf;

            case DeleteResult.NoShrink<K, V>(var newNode, V removed) when newNode == child -> new DeleteResult.NoShrink<>(
                    node, removed
            );

            case DeleteResult.NoShrink<K, V>(var newNode, V removed) -> new DeleteResult.NoShrink<>(
                    node.mutate(keys, children.replace(idx, newNode), exclusive), removed
            );

            case DeleteResult.Shrink<K, V>(var newChild, V removed) -> {
                if (idx > 0 && canBorrow(children.child(idx - 1))) {
                    var donor = children.child(idx - 1);
                    var newNode = switch (donor) {
                        case Node.Leaf<K, V> leafDonor ->
                                borrowFromLeftSibling((Node.Leaf<K, V>) newChild, leafDonor, node, idx - 1);
                        case Node.Internal<K, V> internalDonor ->
                                borrowFromLeftSibling((Node.Internal<K, V>) newChild, internalDonor, node, idx - 1);
                    };

                    yield new DeleteResult.NoShrink<>(newNode, removed);
                }

                if (idx < children.size() - 1 && canBorrow(children.child(idx + 1))) {
                    var donor = children.child(idx + 1);
                    var newNode = switch (donor) {
                        case Node.Leaf<K, V> leafDonor ->
                                borrowFromRightSibling((Node.Leaf<K, V>) newChild, leafDonor, node, idx);
                        case Node.Internal<K, V> internalDonor ->
                                borrowFromRightSibling((Node.Internal<K, V>) newChild, internalDonor, node, idx);
                    };

                    yield new DeleteResult.NoShrink<>(newNode, removed);
                }

                var newNode = switch (newChild) {
                    case Node.Leaf<K, V> rightLeaf when idx > 0 -> merge(
                            (Node.Leaf<K, V>) children.child(idx - 1),
                            rightLeaf,
                            node,
                            idx - 1
                    );
                    case Node.Leaf<K, V> leftLeaf -> merge(
                            leftLeaf,
                            (Node.Leaf<K, V>) children.child(idx + 1),
                            node,
                            idx
                    );
                    case Node.Internal<K, V> rightInternal when idx > 0 -> merge(
                            (Node.Internal<K, V>) children.child(idx - 1),
                            rightInternal,
                            node,
                            idx - 1
                    );
                    case Node.Internal<K, V> leftInternal -> merge(
                            leftInternal,
                            (Node.Internal<K, V>) children.child(idx + 1),
                            node,
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
     * Merges two adjacent branch children when neither can lend a key.
     *
     * <pre>
     *  Before (parent view):
     *      ... | Kp | ...
     *           /   \
     *         left  right
     *
     *  After:
     *      merged.keys     = left.keys ++ [Kp] ++ right.keys
     *      merged.children = left.children ++ right.children
     *      parent drops Kp and one child pointer
     * </pre>
     *
     * @param left      left branch child (smaller keys)
     * @param right     right branch child
     * @param parent    parent holding both siblings
     * @param parentIdx index of Kp in {@code parent.keys()}
     * @return updated parent after merge
     */
    private Node.Internal<K, V> merge(
            Node.Internal<K, V> left,
            Node.Internal<K, V> right,
            Node.Internal<K, V> parent,
            int parentIdx
    ) {

        var keys = parent.keys();
        var children = parent.children();

        var leftKeys = left.keys();
        var leftChildren = left.children();

        var rightKeys = right.keys();
        var rightChildren = right.children();

        var separatorKey = keys.key(parentIdx);

        var mergedKeys = leftKeys.insertAndMerge(leftKeys.size(), separatorKey, rightKeys);
        var mergedChildren = leftChildren.merge(rightChildren);
        var mergedNode = new Node.Internal<>(mergedKeys, mergedChildren);
        exclusive.add(mergedNode);

        var newKeys = keys.remove(parentIdx);
        var newChildren = children.removeAndReplace(parentIdx, mergedNode);

        return parent.mutate(newKeys, newChildren, exclusive);
    }

    /**
     * Merges two adjacent leaf children: concatenates key/value pairs and removes
     * the parent separator between them (leaves do not hold that key).
     *
     * <pre>
     *  merged.keys   = left.keys ++ right.keys
     *  merged.values = left.values ++ right.values
     *  parent loses keys[parentIdx] and one child pointer
     * </pre>
     *
     * @param left      left leaf node
     * @param right     right leaf node
     * @param parent    parent holding both siblings
     * @param parentIdx separator index in {@code parent.keys()} between left and right
     * @return updated parent after merge
     */
    private Node.Internal<K, V> merge(
            Node.Leaf<K, V> left,
            Node.Leaf<K, V> right,
            Node.Internal<K, V> parent,
            int parentIdx
    ) {

        var keys = parent.keys();
        var children = parent.children();

        var leftKeys = left.keys();
        var leftVals = left.values();

        var rightKeys = right.keys();
        var rightVals = right.values();

        var mergedKeys = leftKeys.merge(rightKeys);
        var mergedValues = leftVals.merge(rightVals);
        var mergedNode = new Node.Leaf<>(mergedKeys, mergedValues);
        exclusive.add(mergedNode);

        var newKeys = keys.remove(parentIdx);
        var newChildren = children.removeAndReplace(parentIdx, mergedNode);

        return parent.mutate(newKeys, newChildren, exclusive);
    }

    /**
     * Redistributes from the left sibling branch into the underflowing right sibling
     * via a key rotation through the parent separator at {@code parentIdx}.
     *
     * <pre>
     *  Parent:   ... | Kp | ...
     *               /     \
     *            donor   borrower   (donor.keys.size() &gt; minKeys)
     *
     *  promoted = donor's last key (removed from donor)
     *  borrowed = donor's last child (removed from donor)
     *  Kp prepended to borrower; borrowed child becomes borrower's first child
     *  parent.keys[parentIdx] := promoted
     * </pre>
     *
     * @param borrower  underflowing right branch child
     * @param donor     left sibling with more than {@code minKeys} keys
     * @param parent    parent holding both siblings
     * @param parentIdx index of Kp in {@code parent.keys()}
     * @return updated parent after the borrow
     */
    private Node.Internal<K, V> borrowFromLeftSibling(
            Node.Internal<K, V> borrower,
            Node.Internal<K, V> donor,
            Node.Internal<K, V> parent,
            int parentIdx
    ) {

        var keys = parent.keys();
        var children = parent.children();

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
        var leftNode = donor.mutate(leftKeys, leftChildren, exclusive);

        var rightKeys = borrowerKeys.insert(0, depromotedKey);
        var rightChildren = borrowerChildren.insert(0, borrowedChild);
        var rightNode = borrower.mutate(rightKeys, rightChildren, exclusive);

        var newKeys = keys.replace(parentIdx, promotedKey);
        var newChildren = children.replace(parentIdx, leftNode, rightNode);

        return parent.mutate(newKeys, newChildren, exclusive);
    }

    /**
     * Mirror of {@link #borrowFromLeftSibling(Node.Internal, Node.Internal, Node.Internal, int)}:
     * redistributes from the right sibling into the underflowing left sibling.
     *
     * <pre>
     *  Parent:   ... | Kp | ...
     *               /     \
     *          borrower   donor   (donor.keys.size() &gt; minKeys)
     *
     *  promoted = donor's first key (removed from donor)
     *  borrowed = donor's first child (removed from donor)
     *  Kp appended to borrower; borrowed child becomes borrower's last child
     *  parent.keys[parentIdx] := promoted
     * </pre>
     *
     * @param borrower  underflowing left branch child
     * @param donor     right sibling with more than {@code minKeys} keys
     * @param parent    parent holding both siblings
     * @param parentIdx index of Kp in {@code parent.keys()}
     * @return updated parent after the borrow
     */
    private Node.Internal<K, V> borrowFromRightSibling(
            Node.Internal<K, V> borrower,
            Node.Internal<K, V> donor,
            Node.Internal<K, V> parent,
            int parentIdx
    ) {

        var keys = parent.keys();
        var children = parent.children();

        var donorKeys = donor.keys();
        var donorChildren = donor.children();

        var borrowerKeys = borrower.keys();
        var borrowerChildren = borrower.children();

        var depromotedKey = keys.key(parentIdx);

        var promotedKey = donorKeys.key(0);
        var borrowedChild = donorChildren.child(0);

        var rightKeys = donorKeys.remove(0);
        var rightChildren = donorChildren.remove(0);
        var rightNode = donor.mutate(rightKeys, rightChildren, exclusive);

        var leftKeys = borrowerKeys.insert(borrowerKeys.size(), depromotedKey);
        var leftChildren = borrowerChildren.insert(borrowerChildren.size(), borrowedChild);
        var leftNode = borrower.mutate(leftKeys, leftChildren, exclusive);

        var newKeys = keys.replace(parentIdx, promotedKey);
        var newChildren = children.replace(parentIdx, leftNode, rightNode);

        return parent.mutate(newKeys, newChildren, exclusive);
    }


    /**
     * Redistributes one entry from the left sibling leaf into the underflowing right
     * sibling leaf. {@code donor.keys.size() > minKeys} (see {@link #canBorrow}).
     *
     * <pre>
     *  donor (left)     borrower (right)
     *  [ 10, 20, 30 ]   [ 40 ]          | size 1 &lt; minKeys: underflow
     *
     *  Move last entry (30) from donor to front of borrower:
     *  [ 10, 20 ]       [ 30, 40 ]      | both &gt;= minKeys
     *
     *  parent.keys[parentIdx] := 30  (new first key of right leaf)
     * </pre>
     *
     * @param borrower  right leaf node (underflowed)
     * @param donor     left sibling leaf with spare keys
     * @param parent    parent holding both siblings
     * @param parentIdx separator index in {@code parent.keys()} between donor and borrower
     * @return updated parent after the borrow
     */
    private Node.Internal<K, V> borrowFromLeftSibling(
            Node.Leaf<K, V> borrower,
            Node.Leaf<K, V> donor,
            Node.Internal<K, V> parent,
            int parentIdx
    ) {

        var keys = parent.keys();
        var children = parent.children();

        var donorKeys = donor.keys();
        var donorVals = donor.values();

        var last = donorKeys.size() - 1;
        var borrowedKey = donorKeys.key(last);
        var borrowedVal = donorVals.val(last);

        var leftKeys = donorKeys.remove(last);
        var leftVals = donorVals.remove(last);
        var leftNode = donor.mutate(leftKeys, leftVals, exclusive);

        var rightKeys = borrower.keys().insert(0, borrowedKey);
        var rightVals = borrower.values().insert(0, borrowedVal);
        var rightNode = borrower.mutate(rightKeys, rightVals, exclusive);

        var newKeys = keys.replace(parentIdx, rightKeys.key(0));
        var newChildren = children.replace(parentIdx, leftNode, rightNode);

        return parent.mutate(newKeys, newChildren, exclusive);
    }


    /**
     * Redistributes one entry from the right sibling leaf into the underflowing left
     * sibling leaf. Symmetric to
     * {@link #borrowFromLeftSibling(Node.Leaf, Node.Leaf, Node.Internal, int)}.
     *
     * <pre>
     *  borrower (left)   donor (right)
     *  [ 10 ]            [ 20, 30, 40 ]   | left has 1 &lt; minKeys: underflow
     *
     *  Move first entry (20) from donor to end of borrower:
     *  [ 10, 20 ]        [ 30, 40 ]       | both &gt;= minKeys
     *
     *  parent.keys[parentIdx] := 30  (new first key of right leaf)
     * </pre>
     *
     * @param borrower  left leaf node (underflowed)
     * @param donor     right sibling leaf with spare keys
     * @param parent    parent holding both siblings
     * @param parentIdx separator index in {@code parent.keys()} between borrower and donor
     * @return updated parent after the borrow
     */
    private Node.Internal<K, V> borrowFromRightSibling(
            Node.Leaf<K, V> borrower,
            Node.Leaf<K, V> donor,
            Node.Internal<K, V> parent,
            int parentIdx
    ) {

        var keys = parent.keys();
        var children = parent.children();

        var donorKeys = donor.keys();
        var donorVals = donor.values();

        var borrowedKey = donorKeys.key(0);
        var borrowedVal = donorVals.val(0);

        var rightKeys = donorKeys.remove(0);
        var rightVals = donorVals.remove(0);
        var rightNode = donor.mutate(rightKeys, rightVals, exclusive);

        var leftKeys = borrower.keys()
                .insert(borrower.keys().size(), borrowedKey);
        var leftVals = borrower.values()
                .insert(borrower.values().size(), borrowedVal);
        var leftNode = borrower.mutate(leftKeys, leftVals, exclusive);

        var newKeys = keys.replace(parentIdx, rightKeys.key(0));
        var newChildren = children.replace(parentIdx, leftNode, rightNode);

        return parent.mutate(newKeys, newChildren, exclusive);
    }

    /**
     * Returns {@code true} if {@code node} can donate one key and still satisfy
     * the minimum fill requirement ({@code keys.size() > minKeys}).
     *
     * @param node candidate donor sibling
     * @return {@code true} if a borrow is possible before resorting to merge
     */
    private boolean canBorrow(Node<K, V> node) {
        return node.keys().size() > minKeys;
    }

    /**
     * Returns {@code true} if a node's key count is below the minimum required for
     * a non-root node after deletion ({@code keys.size() < minKeys}).
     *
     * @param keys key storage to check
     * @return {@code true} if the node underflows
     */
    private boolean underflows(KeyStorage<K> keys) {
        return keys.size() < minKeys;
    }


    /**
     * Deletes at the only level that stores values. Returns {@link DeleteResult.Shrink}
     * when the new key count falls below {@link #minKeys} so the parent can rebalance.
     *
     * @param leaf leaf node to remove from
     * @param key  key to remove
     * @return {@link DeleteResult.NotFound} if absent; otherwise shrink or no-shrink
     */
    private DeleteResult<K, V> removeLeaf(Node.Leaf<K, V> leaf, K key) {
        var keys = leaf.keys();
        var vals = leaf.values();
        var idx = Search.find(keys, key);

        if (idx < 0) {
            return new DeleteResult.NotFound<>();
        }

        var removed = vals.val(idx);
        var newKeys = keys.remove(idx);
        var newVals = vals.remove(idx);
        var newLeaf = leaf.mutate(newKeys, newVals, exclusive);

        return underflows(newKeys)
                ? new DeleteResult.Shrink<>(newLeaf, removed)
                : new DeleteResult.NoShrink<>(newLeaf, removed);
    }
    /**
     * Returns whether {@code key} is visible in this transaction state.
     *
     * @param key key to test
     * @return {@code true} if present in current transactional state
     * @throws IllegalStateException if the transaction is already committed
     */
    @Override
    public boolean contains(K key) {
        throwIfCommitted();
        return query.contains(us.root(), key);
    }
    /**
     * Returns the number of entries currently visible in this transaction state.
     *
     * @return transactional size
     * @throws IllegalStateException if the transaction is already committed
     */
    @Override
    public int size() {
        throwIfCommitted();
        return us.size();
    }
    /**
     * Returns the value currently visible for {@code key} in this transaction.
     *
     * @param key key to resolve
     * @return value currently visible in transaction state, if present
     * @throws IllegalStateException if the transaction is already committed
     */
    @Override
    public Optional<V> get(K key) {
        throwIfCommitted();
        return query.get(us.root(), key);
    }

    /**
     * Returns an iterator over all entries in the current transaction state in
     * {@code direction} order, transforming each entry with {@code mapper}.
     *
     * @param direction traversal direction
     * @param mapper    function applied to each key-value pair
     * @param <R>       iterator element type
     * @return iterator over the current working state
     * @throws IllegalStateException if the transaction is already committed
     */
    @Override
    public <R> Iterator<R> iterator(Direction direction, BiFunction<K, V, R> mapper) {
        throwIfCommitted();
        return query.iterator(us.root(), direction, mapper);
    }

    /**
     * Returns an iterator over entries in {@code range} within the current
     * transaction state, in {@code direction} order.
     *
     * @param direction traversal direction
     * @param range     range bounds and endpoint policy
     * @param mapper    function applied to each key-value pair
     * @param <R>       iterator element type
     * @return range-bounded iterator over the current working state
     * @throws IllegalStateException if the transaction is already committed
     */
    @Override
    public <R> Iterator<R> iterator(Direction direction, Range<K> range, BiFunction<K, V, R> mapper) {
        throwIfCommitted();
        return query.iterator(us.root(), direction, range, mapper);
    }

    /**
     * Applies {@code consumer} to all entries in the current transaction state in
     * {@code direction} order.
     *
     * @param direction traversal direction
     * @param consumer  action applied to each key-value pair
     * @throws IllegalStateException if the transaction is already committed
     */
    @Override
    public void forEach(Direction direction, BiConsumer<K, V> consumer) {
        throwIfCommitted();
        query.forEach(us.root(), direction, consumer);
    }

    /**
     * Applies {@code consumer} to entries in {@code range} within the current
     * transaction state, in {@code direction} order.
     *
     * @param direction traversal direction
     * @param range     range bounds and endpoint policy
     * @param consumer  action applied to each key-value pair
     * @throws IllegalStateException if the transaction is already committed
     */
    @Override
    public void forEach(Direction direction, Range<K> range, BiConsumer<K, V> consumer) {
        throwIfCommitted();
        query.forEach(us.root(), direction, range, consumer);
    }
    /**
     * Returns the base committed snapshot captured when this transaction started.
     *
     * <p>The returned snapshot does not include writes performed through this
     * transaction.
     *
     * @return base snapshot at transaction start
     * @throws IllegalStateException if the transaction is already committed
     */
    @Override
    public Snapshot<K, V> snapshot() {
        throwIfCommitted();
        return snapshot;
    }
    /**
     * Returns whether this transaction has already been committed.
     *
     * @return {@code true} if committed, otherwise {@code false}
     */
    @Override
    public boolean committed() {
        return committed;
    }
    /**
     * Commits the current uncommitted state and invalidates this transaction.
     *
     * @throws IllegalStateException if the transaction is already committed
     */
    @Override
    public void commit() {
        throwIfCommitted();
        committer.commit(us);
        committed = true;
        exclusive.clear();
    }
}

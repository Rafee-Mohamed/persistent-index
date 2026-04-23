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

public class PersistentBPlusTreeTxn<K, V> implements Txn<K, V> {

    private final StateCommitter<K, V> committer;
    private final UncommittedState<K, V> us;
    private final KeyStorageFactory<K> ksf;
    private final Snapshot<K, V> snapshot;
    private final ReadQuery<K, V> query;
    private final Set<Node<K, V>> exclusive;

    private final int maxKeys;
    private final int minKeys;

    private boolean committed;

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

    public void throwIfCommitted() {
        if (committed) {
            throw new IllegalStateException("Txn use after committed");
        }
    }

    @Override
    public Optional<V> put(K key, V value) {
        throwIfCommitted();
        if (us.isEmpty()) {
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

        if (result.replaced() != null) {
            us.increment();
        }

        return Optional.ofNullable(result.replaced());
    }



    private PutResult<K, V> put(Node<K, V> node, K key, V val) {
        return switch (node) {
            case Node.Internal<K, V> internal -> putInternal(internal, key, val);
            case Node.Leaf<K, V> leaf -> putLeaf(leaf, key, val);
        };
    }

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

    public DeleteResult<K, V> remove(Node<K, V> node, K key) {
        return switch (node) {
            case Node.Internal<K, V> internal -> removeInternal(internal, key);
            case Node.Leaf<K, V> leaf -> removeLeaf(leaf, key);
        };
    }

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

    private boolean canBorrow(Node<K, V> node) {
        return node.keys().size() > minKeys;
    }

    private boolean underflows(KeyStorage<K> keys) {
        return keys.size() < minKeys;
    }


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

    @Override
    public boolean contains(K key) {
        throwIfCommitted();
        return query.contains(us.root(), key);
    }

    @Override
    public int size() {
        throwIfCommitted();
        return us.size();
    }

    @Override
    public Optional<V> get(K key) {
        throwIfCommitted();
        return query.get(us.root(), key);
    }

    @Override
    public <R> Iterator<R> iterator(Direction direction, BiFunction<K, V, R> mapper) {
        throwIfCommitted();
        return query.iterator(us.root(), direction, mapper);
    }

    @Override
    public <R> Iterator<R> iterator(Direction direction, Range<K> range, BiFunction<K, V, R> mapper) {
        throwIfCommitted();
        return query.iterator(us.root(), direction, range, mapper);
    }

    @Override
    public void forEach(Direction direction, BiConsumer<K, V> consumer) {
        throwIfCommitted();
        query.forEach(us.root(), direction, consumer);
    }

    @Override
    public void forEach(Direction direction, Range<K> range, BiConsumer<K, V> consumer) {
        throwIfCommitted();
        query.forEach(us.root(), direction, range, consumer);
    }

    @Override
    public Snapshot<K, V> snapshot() {
        throwIfCommitted();
        return snapshot;
    }

    @Override
    public boolean committed() {
        return committed;
    }

    @Override
    public void commit() {
        throwIfCommitted();
        committer.commit(us);
        committed = true;
        exclusive.clear();
    }
}

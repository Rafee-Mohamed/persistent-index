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

    @Override
    public Optional<V> remove(K key) {
        return Optional.empty();
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
    public Snapshot<K, V> committed() {
        throwIfCommitted();
        return snapshot;
    }

    @Override
    public void commit() {
        throwIfCommitted();
        committer.commit(us);
        committed = true;
        exclusive.clear();
    }
}

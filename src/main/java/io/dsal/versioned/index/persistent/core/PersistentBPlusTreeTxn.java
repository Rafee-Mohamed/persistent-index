package io.dsal.versioned.index.persistent.core;

import io.dsal.versioned.index.api.Direction;
import io.dsal.versioned.index.api.Range;
import io.dsal.versioned.index.api.Txn;
import io.dsal.versioned.index.persistent.layout.KeyStorageFactory;

import java.util.Iterator;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

public class PersistentBPlusTreeTxn<K, V> implements Txn<K, V> {

    private final StateCommitter<K, V> committer;
    private final UncommittedState<K, V> us;
    private final KeyStorageFactory<K> ksf;
    private final ReadQuery<K, V> query;

    private final int maxKeys;
    private final int minKeys;

    public PersistentBPlusTreeTxn(StateCommitter<K, V> committer, KeyStorageFactory<K> ksf, ReadQuery<K, V> query, int maxKeys, int minKeys) {
        this.us = new UncommittedState<>(committer.committed());
        this.committer = committer;
        this.ksf = ksf;
        this.query = query;
        this.maxKeys = maxKeys;
        this.minKeys = minKeys;

    }

    @Override
    public Optional<V> put(K key, V value) {
        return Optional.empty();
    }

    @Override
    public Optional<V> remove(K key) {
        return Optional.empty();
    }

    @Override
    public boolean contains(K key) {
        return query.contains(us.root(), key);
    }

    @Override
    public int size() {
        return us.size();
    }

    @Override
    public Optional<V> get(K key) {
        return query.get(us.root(), key);
    }

    @Override
    public <R> Iterator<R> iterator(Direction direction, BiFunction<K, V, R> mapper) {
        return query.iterator(us.root(), direction, mapper);
    }

    @Override
    public <R> Iterator<R> iterator(Direction direction, Range<K> range, BiFunction<K, V, R> mapper) {
        return query.iterator(us.root(), direction, range, mapper);
    }

    @Override
    public void forEach(Direction direction, BiConsumer<K, V> consumer) {
        query.forEach(us.root(), direction, consumer);
    }

    @Override
    public void forEach(Direction direction, Range<K> range, BiConsumer<K, V> consumer) {
        query.forEach(us.root(), direction, range, consumer);
    }

    @Override
    public void commit() {
        committer.commit(us);
    }
}

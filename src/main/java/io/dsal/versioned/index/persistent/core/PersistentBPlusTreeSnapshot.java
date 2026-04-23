package io.dsal.versioned.index.persistent.core;

import io.dsal.versioned.index.api.Direction;
import io.dsal.versioned.index.api.Range;
import io.dsal.versioned.index.api.Snapshot;

import java.util.Iterator;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

public class PersistentBPlusTreeSnapshot<K, V> implements Snapshot<K, V> {

    private final CommittedState<K, V> cs;
    private final ReadQuery<K, V> query;

    public PersistentBPlusTreeSnapshot(CommittedState<K, V> cs, ReadQuery<K, V> query) {
        this.cs = cs;
        this.query = query;
    }

    @Override
    public boolean contains(K key) {
        return query.contains(cs.root(), key);
    }

    @Override
    public int size() {
        return cs.size();
    }

    @Override
    public Optional<V> get(K key) {
        return query.get(cs.root(), key);
    }

    @Override
    public <R> Iterator<R> iterator(Direction direction, BiFunction<K, V, R> mapper) {
        return query.iterator(cs.root(), direction, mapper);
    }

    @Override
    public <R> Iterator<R> iterator(Direction direction, Range<K> range, BiFunction<K, V, R> mapper) {
        return query.iterator(cs.root(), direction, range, mapper);
    }

    @Override
    public void forEach(Direction direction, BiConsumer<K, V> consumer) {
        query.forEach(cs.root(), direction, consumer);
    }

    @Override
    public void forEach(Direction direction, Range<K> range, BiConsumer<K, V> consumer) {
        query.forEach(cs.root(), direction, range, consumer);
    }
}

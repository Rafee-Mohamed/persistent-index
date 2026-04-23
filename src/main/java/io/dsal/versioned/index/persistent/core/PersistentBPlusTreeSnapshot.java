package io.dsal.versioned.index.persistent.core;

import io.dsal.versioned.index.api.Direction;
import io.dsal.versioned.index.api.Entry;
import io.dsal.versioned.index.api.Range;
import io.dsal.versioned.index.api.Snapshot;

import java.util.Iterator;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

public class PersistentBPlusTreeSnapshot<K, V> implements Snapshot<K, V> {

    private final CommittedState<K, V> cs;

    public PersistentBPlusTreeSnapshot(CommittedState<K, V> cs) {
        this.cs = cs;
    }

    @Override
    public boolean contains(K key) {
        return false;
    }

    @Override
    public int size() {
        return 0;
    }

    @Override
    public Optional<V> get(K key) {
        return Optional.empty();
    }

    @Override
    public Iterator<Entry<K, V>> iterator(Direction direction) {
        return null;
    }

    @Override
    public Iterator<Entry<K, V>> iterator(Range<K> range, Direction direction) {
        return null;
    }

    @Override
    public <R> Iterator<R> iterator(Direction direction, BiFunction<K, V, R> mapper) {
        return null;
    }

    @Override
    public <R> Iterator<R> iterator(Range<K> range, Direction direction, BiFunction<K, V, R> mapper) {
        return null;
    }

    @Override
    public void forEach(Direction direction, BiConsumer<K, V> consumer) {

    }

    @Override
    public void forEach(Range<K> range, Direction direction, BiConsumer<K, V> consumer) {

    }
}

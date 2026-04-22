package io.dsal.versioned.index.core.impl;

import io.dsal.versioned.index.core.api.Direction;
import io.dsal.versioned.index.core.api.Entry;
import io.dsal.versioned.index.core.api.Range;
import io.dsal.versioned.index.core.api.Txn;

import java.util.Iterator;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

public class PersistentBPlusTreeTxn<K, V> implements Txn<K, V> {

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

    @Override
    public void commit() {

    }
}

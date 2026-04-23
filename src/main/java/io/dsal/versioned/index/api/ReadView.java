package io.dsal.versioned.index.api;

import java.util.Iterator;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

public interface ReadView<K,V> {
    boolean contains(K key);
    int size();
    Optional<V> get(K key);

    default Iterator<? extends Entry<K,V>> iterator(Direction direction) {
        return iterator(direction, DefaultEntry::new);
    }

    default Iterator<? extends Entry<K,V>> iterator() {
        return iterator(Direction.ASC);
    }

    default Iterator<? extends Entry<K,V>> iterator(Direction direction, Range<K> range) {
        return iterator(direction, range, DefaultEntry::new);
    }

    default Iterator<? extends Entry<K,V>> iterator(Range<K> range) {
        return iterator(Direction.ASC, range);
    }

    <R> Iterator<R> iterator(Direction direction, BiFunction<K,V,R> mapper);

    default <R> Iterator<R> iterator(BiFunction<K,V,R> mapper) {
        return iterator(Direction.ASC, mapper);
    }

    <R> Iterator<R> iterator(Direction direction, Range<K> range, BiFunction<K,V,R> mapper);

    default <R> Iterator<R> iterator(Range<K> range, BiFunction<K,V,R> mapper) {
        return iterator(Direction.ASC, range, mapper);
    }

    void forEach(Direction direction, BiConsumer<K,V> consumer);

    default void forEach(BiConsumer<K,V> consumer) {
        forEach(Direction.ASC, consumer);
    }

    void forEach(Direction direction, Range<K> range, BiConsumer<K,V> consumer);

    default void forEach(Range<K> range, BiConsumer<K,V> consumer) {
        forEach(Direction.ASC, range, consumer);
    }
}

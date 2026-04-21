package io.dsal.versioned.index.core.api;

import java.util.Iterator;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

public interface ReadView<K,V> {
    Optional<V> get(K key);

    Iterator<Entry<K,V>> iterator(Direction direction);

    default Iterator<Entry<K,V>> iterator() {
        return iterator(Direction.ASC);
    }

    Iterator<Entry<K,V>> iterator(Range<K> range, Direction direction);

    default Iterator<Entry<K,V>> iterator(Range<K> range) {
        return iterator(range, Direction.ASC);
    }

    <R> Iterator<R> iterator(Direction direction, BiFunction<K,V,R> mapper);

    default <R> Iterator<R> iterator(BiFunction<K,V,R> mapper) {
        return iterator(Direction.ASC, mapper);
    }

    <R> Iterator<R> iterator(Range<K> range, Direction direction, BiFunction<K,V,R> mapper);

    default <R> Iterator<R> iterator(Range<K> range, BiFunction<K,V,R> mapper) {
        return iterator(range, Direction.ASC, mapper);
    }

    void forEach(Direction direction, BiConsumer<K,V> consumer);

    default void forEach(BiConsumer<K,V> consumer) {
        forEach(Direction.ASC, consumer);
    }

    void forEach(Range<K> range, Direction direction, BiConsumer<K,V> consumer);

    default void forEach(Range<K> range, BiConsumer<K,V> consumer) {
        forEach(range, Direction.ASC, consumer);
    }
}

package io.dsal.versioned.index.api;

import java.util.Iterator;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

public interface OrderedVersionedIndex<K, V> extends ReadView<K, V>, Mutator<K, V> {

    Snapshot<K, V> snapshot();

    Txn<K, V> txn();

     default <R, E extends Exception> R txn(TxnBlock<K, V, R, E> block) throws E {
        var txn = txn();
        var result = block.apply(txn);
        txn.commit();
        return result;
    }

    @Override
    default boolean contains(K key) {
        return snapshot().contains(key);
    }

    @Override
    default int size() {
        return snapshot().size();
    }

    @Override
    default Optional<V> get(K key) {
        return snapshot().get(key);
    }

    @Override
    default <R> Iterator<R> iterator(Direction direction, BiFunction<K, V, R> mapper) {
        return snapshot().iterator(direction, mapper);
    }

    @Override
    default <R> Iterator<R> iterator(Direction direction, Range<K> range, BiFunction<K, V, R> mapper) {
        return snapshot().iterator(direction, range, mapper);
    }

    @Override
    default void forEach(Direction direction, BiConsumer<K, V> consumer) {
        snapshot().forEach(direction, consumer);
    }

    @Override
    default void forEach(Direction direction, Range<K> range, BiConsumer<K, V> consumer) {
        snapshot().forEach(direction, range, consumer);
    }

    @Override
    default Optional<V> put(K key, V value) {
        return txn(th -> th.put(key, value));
    }

    @Override
    default Optional<V> remove(K key) {
        return txn(th -> th.remove(key));
    }
}

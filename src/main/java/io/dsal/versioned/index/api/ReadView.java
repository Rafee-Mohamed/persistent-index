package io.dsal.versioned.index.api;

import java.util.Iterator;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

/**
 * Read-only ordered view over key-value mappings.
 *
 * <p>This interface defines read shape only. Consistency semantics depend on
 * the concrete view:
 * <ul>
 *   <li>{@link Snapshot}: stable point-in-time reads</li>
 *   <li>{@link TxnHandle}: reads from mutable transaction working state</li>
 * </ul>
 *
 * <p>Ordering is defined by key order, and directional/ranged traversal is
 * provided through {@link Direction} and {@link Range}.
 *
 * @param <K> key type
 * @param <V> value type
 */
public interface ReadView<K,V> {
    /**
     * Returns {@code true} if this view contains a mapping for the specified key.
     *
     * @param key key whose presence is to be tested
     * @return {@code true} if a mapping exists, otherwise {@code false}
     * @throws IllegalStateException if this view can no longer be used
     */
    boolean contains(K key);

    /**
     * Returns the number of mappings currently visible in this view.
     *
     * @return number of visible mappings
     * @throws IllegalStateException if this view can no longer be used
     */
    int size();

    /**
     * Returns the value mapped to the specified key, if any.
     *
     * @param key key whose associated value is to be returned
     * @return optional containing the mapped value, or empty if absent
     * @throws IllegalStateException if this view can no longer be used
     */
    Optional<V> get(K key);

    /**
     * Returns an iterator over entries in the specified direction.
     *
     * @param direction iteration direction
     * @return iterator over entries in key order for the given direction
     * @throws NullPointerException if {@code direction} is null
     * @throws IllegalStateException if this view can no longer be used
     */
    default Iterator<? extends Entry<K,V>> iterator(Direction direction) {
        return iterator(direction, DefaultEntry::new);
    }

    /**
     * Returns an iterator over entries in ascending key order.
     *
     * @return iterator over entries in ascending key order
     * @throws IllegalStateException if this view can no longer be used
     */
    default Iterator<? extends Entry<K,V>> iterator() {
        return iterator(Direction.ASC);
    }

    /**
     * Returns an iterator over entries in the specified range and direction.
     *
     * @param direction iteration direction
     * @param range key range restriction
     * @return iterator over entries that satisfy {@code range}
     * @throws NullPointerException if {@code direction} or {@code range} is null
     * @throws IllegalStateException if this view can no longer be used
     */
    default Iterator<? extends Entry<K,V>> iterator(Direction direction, Range<K> range) {
        return iterator(direction, range, DefaultEntry::new);
    }

    /**
     * Returns an iterator over entries in the specified range in ascending order.
     *
     * @param range key range restriction
     * @return iterator over entries that satisfy {@code range}
     * @throws NullPointerException if {@code range} is null
     * @throws IllegalStateException if this view can no longer be used
     */
    default Iterator<? extends Entry<K,V>> iterator(Range<K> range) {
        return iterator(Direction.ASC, range);
    }

    /**
     * Returns an iterator over mapped results in the specified direction.
     *
     * @param direction iteration direction
     * @param mapper function applied to each key-value pair
     * @param <R> mapped element type
     * @return iterator over mapped values
     * @throws NullPointerException if {@code direction} or {@code mapper} is null
     * @throws IllegalStateException if this view can no longer be used
     */
    <R> Iterator<R> iterator(Direction direction, BiFunction<K,V,R> mapper);

    /**
     * Returns an iterator over mapped results in ascending key order.
     *
     * @param mapper function applied to each key-value pair
     * @param <R> mapped element type
     * @return iterator over mapped values
     * @throws NullPointerException if {@code mapper} is null
     * @throws IllegalStateException if this view can no longer be used
     */
    default <R> Iterator<R> iterator(BiFunction<K,V,R> mapper) {
        return iterator(Direction.ASC, mapper);
    }

    /**
     * Returns an iterator over mapped results in the specified range and direction.
     *
     * @param direction iteration direction
     * @param range key range restriction
     * @param mapper function applied to each key-value pair
     * @param <R> mapped element type
     * @return iterator over mapped values that satisfy {@code range}
     * @throws NullPointerException if {@code direction}, {@code range}, or {@code mapper} is null
     * @throws IllegalStateException if this view can no longer be used
     */
    <R> Iterator<R> iterator(Direction direction, Range<K> range, BiFunction<K,V,R> mapper);

    /**
     * Returns an iterator over mapped results in the specified range in ascending order.
     *
     * @param range key range restriction
     * @param mapper function applied to each key-value pair
     * @param <R> mapped element type
     * @return iterator over mapped values that satisfy {@code range}
     * @throws NullPointerException if {@code range} or {@code mapper} is null
     * @throws IllegalStateException if this view can no longer be used
     */
    default <R> Iterator<R> iterator(Range<K> range, BiFunction<K,V,R> mapper) {
        return iterator(Direction.ASC, range, mapper);
    }

    /**
     * Applies the specified action to each entry in the given direction.
     *
     * @param direction iteration direction
     * @param consumer action to perform for each key-value pair
     * @throws NullPointerException if {@code direction} or {@code consumer} is null
     * @throws IllegalStateException if this view can no longer be used
     */
    void forEach(Direction direction, BiConsumer<K,V> consumer);

    /**
     * Applies the specified action to each entry in ascending key order.
     *
     * @param consumer action to perform for each key-value pair
     * @throws NullPointerException if {@code consumer} is null
     * @throws IllegalStateException if this view can no longer be used
     */
    default void forEach(BiConsumer<K,V> consumer) {
        forEach(Direction.ASC, consumer);
    }

    /**
     * Applies the specified action to each entry in the given range and direction.
     *
     * @param direction iteration direction
     * @param range key range restriction
     * @param consumer action to perform for each key-value pair
     * @throws NullPointerException if {@code direction}, {@code range}, or {@code consumer} is null
     * @throws IllegalStateException if this view can no longer be used
     */
    void forEach(Direction direction, Range<K> range, BiConsumer<K,V> consumer);

    /**
     * Applies the specified action to each entry in the given range in ascending order.
     *
     * @param range key range restriction
     * @param consumer action to perform for each key-value pair
     * @throws NullPointerException if {@code range} or {@code consumer} is null
     * @throws IllegalStateException if this view can no longer be used
     */
    default void forEach(Range<K> range, BiConsumer<K,V> consumer) {
        forEach(Direction.ASC, range, consumer);
    }
}

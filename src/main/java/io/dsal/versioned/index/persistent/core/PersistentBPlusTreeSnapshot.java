package io.dsal.versioned.index.persistent.core;

import io.dsal.versioned.index.api.Direction;
import io.dsal.versioned.index.api.Range;
import io.dsal.versioned.index.api.Snapshot;

import java.util.Iterator;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

/**
 * Immutable read-only snapshot over a committed B+ tree state.
 *
 * <p>The snapshot binds to one {@link CommittedState} instance and remains stable
 * even if later transactions commit new roots. All operations delegate to
 * {@link ReadQuery} using the bound committed root.
 *
 * @param <K> key type
 * @param <V> value type
 */
public class PersistentBPlusTreeSnapshot<K, V> implements Snapshot<K, V> {

    private final CommittedState<K, V> cs;
    private final ReadQuery<K, V> query;

    /**
     * Creates a snapshot over the given committed state.
     *
     * @param cs committed state to expose
     * @param query read engine used for lookups, scans, and range iteration
     */
    public PersistentBPlusTreeSnapshot(CommittedState<K, V> cs, ReadQuery<K, V> query) {
        this.cs = cs;
        this.query = query;
    }

    /**
     * Returns whether {@code key} exists in the committed state this snapshot was
     * created from.
     *
     * @param key key to test
     * @return {@code true} if present
     */
    @Override
    public boolean contains(K key) {
        return query.contains(cs.root(), key);
    }

    /**
     * Returns the number of entries in the committed state this snapshot was
     * created from.
     *
     * @return snapshot size
     */
    @Override
    public int size() {
        return cs.size();
    }

    /**
     * Returns the value mapped to {@code key} in the committed state this snapshot
     * was created from.
     *
     * @param key key to resolve
     * @return value at snapshot time, if present
     */
    @Override
    public Optional<V> get(K key) {
        return query.get(cs.root(), key);
    }

    /**
     * Returns an iterator over all snapshot entries in {@code direction} order,
     * transforming each entry with {@code mapper}.
     *
     * @param direction traversal direction
     * @param mapper    function applied to each key-value pair
     * @param <R>       iterator element type
     * @return iterator over the snapshot
     */
    @Override
    public <R> Iterator<R> iterator(Direction direction, BiFunction<K, V, R> mapper) {
        return query.iterator(cs.root(), direction, mapper);
    }

    /**
     * Returns an iterator over snapshot entries in {@code range} and
     * {@code direction} order.
     *
     * @param direction traversal direction
     * @param range     range bounds and endpoint policy
     * @param mapper    function applied to each key-value pair
     * @param <R>       iterator element type
     * @return range-bounded iterator over the snapshot
     */
    @Override
    public <R> Iterator<R> iterator(Direction direction, Range<K> range, BiFunction<K, V, R> mapper) {
        return query.iterator(cs.root(), direction, range, mapper);
    }

    /**
     * Applies {@code consumer} to all snapshot entries in {@code direction} order.
     *
     * @param direction traversal direction
     * @param consumer  action applied to each key-value pair
     */
    @Override
    public void forEach(Direction direction, BiConsumer<K, V> consumer) {
        query.forEach(cs.root(), direction, consumer);
    }

    /**
     * Applies {@code consumer} to snapshot entries in {@code range} and
     * {@code direction} order.
     *
     * @param direction traversal direction
     * @param range     range bounds and endpoint policy
     * @param consumer  action applied to each key-value pair
     */
    @Override
    public void forEach(Direction direction, Range<K> range, BiConsumer<K, V> consumer) {
        query.forEach(cs.root(), direction, range, consumer);
    }
}

package io.dsal.versioned.index.api;

import java.util.Iterator;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

/**
 * A versioned ordered key-value index that provides snapshot-isolated reads,
 * atomic multi-operation transactions, and non-blocking reader-writer semantics
 * where readers never block the writer and the writer never blocks readers.
 *
 * <p>This interface combines:
 * <ul>
 *   <li>ordered reads from {@link ReadView}</li>
 *   <li>mutations from {@link Mutator}</li>
 *   <li>explicit transactional control via {@link #txn()}</li>
 * </ul>
 *
 * <h2>Concurrency contract (MVCC): readers never block the writer; the writer never blocks readers</h2>
 *
 * <p>Reads and writes are non-blocking with respect to each other:
 * <ul>
 *   <li><b>Readers never block the writer</b>: an in-progress read imposes
 *       no constraint on a concurrent or same-thread write transaction.</li>
 *   <li><b>The writer never blocks readers</b>: an in-progress write transaction
 *       imposes no constraint on acquiring or reading any snapshot, including
 *       on the same thread.</li>
 * </ul>
 *
 * <p>Only one writer may hold an open transaction at a time (single-writer);
 * concurrent write transactions lead to lost updates and require external
 * coordination. Concurrent readers require none.
 *
 * <p>{@link Txn} instances are mutable and must be thread-confined.
 *
 * <p><b>Read semantics</b>:
 * <ul>
 *   <li>default read methods in this interface delegate to {@link #snapshot()}</li>
 *   <li>each read call observes committed state only</li>
 *   <li>for multi-step stable reads, callers should capture one snapshot and read from it</li>
 * </ul>
 *
 * <p><b>Mutation semantics</b>:
 * <ul>
 *   <li>{@link #put put(K, V)} and {@link #remove remove(K)} run as single-operation transactions</li>
 *   <li>{@link #txn()} supports multi-operation transactions with one commit point</li>
 *   <li>on successful commit, all transaction mutations become visible atomically</li>
 * </ul>
 *
 * <p><b>Equivalent behavior of defaults</b>:
 * <pre>{@code
 * OrderedVersionedIndex<String, Integer> index = ...;
 *
 * Optional<Integer> value = index.get("k1");
 * // same semantics as:
 * Optional<Integer> value2 = index.snapshot().get("k1");
 *
 * Optional<Integer> old = index.put("k1", 42);
 * // same semantics as:
 * Optional<Integer> old2 = index.txn(th -> th.put("k1", 42));
 * }</pre>
 *
 * @param <K> key type
 * @param <V> value type
 */
public interface OrderedVersionedIndex<K, V> extends ReadView<K, V>, Mutator<K, V> {

    /**
     * Returns a snapshot of the latest committed state.
     *
     * <p>The returned snapshot is stable and is not affected by future commits.
     *
     * <p>Callers inside an active transaction can use {@link Txn#snapshot()} instead,
     * which returns the same committed state at transaction creation time
     * without an additional call to this method.
     *
     * @return snapshot over latest committed state
     */
    Snapshot<K, V> snapshot();

    /**
     * Starts a new transaction over the current committed state.
     *
     * @return new transaction handle
     */
    Txn<K, V> txn();

    /**
     * Executes the given transactional block and commits on normal completion.
     *
     * <p>If {@code block} throws, commit is not attempted and the same exception
     * is rethrown.
     *
     * @param block transactional callback
     * @param <R> callback result type
     * @param <E> checked exception type thrown by the callback
     * @return result produced by {@code block}
     * @throws NullPointerException if {@code block} is null
     * @throws E if {@code block} throws
     * @throws IllegalStateException if commit fails because the transaction is invalid
     */
    default <R, E extends Exception> R txn(TxnBlock<K, V, R, E> block) throws E {
        var txn = txn();
        var result = block.apply(txn);
        txn.commit();
        return result;
    }

    /**
     * Executes the given transactional action and commits on normal completion.
     *
     * <p>If {@code action} throws, commit is not attempted and the same exception
     * is rethrown.
     *
     * @param action transactional callback
     * @param <E> checked exception type thrown by the callback
     * @throws NullPointerException if {@code action} is null
     * @throws E if {@code action} throws
     * @throws IllegalStateException if commit fails because the transaction is invalid
     */
    default <E extends Exception> void txn(TxnAction<K, V, E> action) throws E {
        var txn = txn();
        action.apply(txn);
        txn.commit();
    }

    /**
     * Returns whether the key is visible in the latest committed state.
     *
     * <p>Equivalent to {@code snapshot().contains(key)}.
     *
     * @param key key whose presence is tested
     * @return {@code true} if visible in the latest committed state
     * @throws IllegalStateException if this index can no longer be used
     */
    @Override
    default boolean contains(K key) {
        return snapshot().contains(key);
    }

    /**
     * Returns the size of the latest committed state.
     *
     * <p>Equivalent to {@code snapshot().size()}.
     *
     * @return number of committed mappings currently visible
     * @throws IllegalStateException if this index can no longer be used
     */
    @Override
    default int size() {
        return snapshot().size();
    }

    /**
     * Returns the committed value currently mapped to {@code key}, if present.
     *
     * <p>Equivalent to {@code snapshot().get(key)}.
     *
     * @param key key whose mapped value is returned
     * @return committed value for {@code key}, or empty if absent
     * @throws IllegalStateException if this index can no longer be used
     */
    @Override
    default Optional<V> get(K key) {
        return snapshot().get(key);
    }

    /**
     * Returns an iterator over mapped committed entries in the given direction.
     *
     * <p>Equivalent to {@code snapshot().iterator(direction, mapper)}.
     *
     * @param direction iteration direction
     * @param mapper mapping function
     * @param <R> mapped element type
     * @return iterator over mapped committed entries
     * @throws NullPointerException if {@code direction} or {@code mapper} is null
     * @throws IllegalStateException if this index can no longer be used
     */
    @Override
    default <R> Iterator<R> iterator(Direction direction, BiFunction<K, V, R> mapper) {
        return snapshot().iterator(direction, mapper);
    }

    /**
     * Returns an iterator over mapped committed entries in the given range and direction.
     *
     * <p>Equivalent to {@code snapshot().iterator(direction, range, mapper)}.
     *
     * @param direction iteration direction
     * @param range key range restriction
     * @param mapper mapping function
     * @param <R> mapped element type
     * @return iterator over mapped committed range entries
     * @throws NullPointerException if {@code direction}, {@code range}, or {@code mapper} is null
     * @throws IllegalStateException if this index can no longer be used
     */
    @Override
    default <R> Iterator<R> iterator(Direction direction, Range<K> range, BiFunction<K, V, R> mapper) {
        return snapshot().iterator(direction, range, mapper);
    }

    /**
     * Applies {@code consumer} to each committed entry in the given direction.
     *
     * <p>Equivalent to {@code snapshot().forEach(direction, consumer)}.
     *
     * @param direction iteration direction
     * @param consumer action applied to each entry
     * @throws NullPointerException if {@code direction} or {@code consumer} is null
     * @throws IllegalStateException if this index can no longer be used
     */
    @Override
    default void forEach(Direction direction, BiConsumer<K, V> consumer) {
        snapshot().forEach(direction, consumer);
    }

    /**
     * Applies {@code consumer} to each committed entry in the given range and direction.
     *
     * <p>Equivalent to {@code snapshot().forEach(direction, range, consumer)}.
     *
     * @param direction iteration direction
     * @param range key range restriction
     * @param consumer action applied to each entry
     * @throws NullPointerException if {@code direction}, {@code range}, or {@code consumer} is null
     * @throws IllegalStateException if this index can no longer be used
     */
    @Override
    default void forEach(Direction direction, Range<K> range, BiConsumer<K, V> consumer) {
        snapshot().forEach(direction, range, consumer);
    }

    /**
     * Associates {@code value} with {@code key} as a single-operation transaction.
     *
     * <p>Equivalent to {@code txn(th -> th.put(key, value))}.
     *
     * @param key key with which the value is associated
     * @param value value to store
     * @return previous value for {@code key}, or empty if absent
     * @throws IllegalStateException if this index can no longer be used
     */
    @Override
    default Optional<V> put(K key, V value) {
        return txn(th -> {
            return th.put(key, value);
        });
    }

    /**
     * Removes {@code key} as a single-operation transaction.
     *
     * <p>Equivalent to {@code txn(th -> th.remove(key))}.
     *
     * @param key key whose mapping is removed
     * @return removed value, if the key was present
     * @throws IllegalStateException if this index can no longer be used
     */
    @Override
    default Optional<V> remove(K key) {
        return txn(th -> {
            return th.remove(key);
        });
    }
}

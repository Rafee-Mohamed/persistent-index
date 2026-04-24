package io.dsal.versioned.index.api;

import java.util.Optional;

/**
 * Mutation operations for an ordered key-value index view.
 *
 * <p>Methods return the previous value associated with the key, if present.
 *
 * <p>Atomicity and visibility depend on the owning type:
 * <ul>
 *   <li>in {@link TxnHandle}, mutations update transaction-local working state</li>
 *   <li>in {@link OrderedVersionedIndex} defaults, each call runs as a single-operation transaction</li>
 * </ul>
 *
 * @param <K> key type
 * @param <V> value type
 */
public interface Mutator<K, V> {

    /**
     * Associates the specified value with the specified key.
     *
     * <p>If the key is already present, its value is replaced.
     *
     * @param key key with which the specified value is associated
     * @param value value to associate with the specified key
     * @return previous value associated with {@code key}, or empty if the key was absent
     * @throws IllegalStateException if this mutator can no longer be used
     */
    Optional<V> put(K key, V value);

    /**
     * Removes the mapping for the specified key if present.
     *
     * @param key key whose mapping is to be removed
     * @return previous value associated with {@code key}, or empty if no mapping existed
     * @throws IllegalStateException if this mutator can no longer be used
     */
    Optional<V> remove(K key);
}

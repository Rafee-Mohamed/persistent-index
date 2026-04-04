package io.dsal.persistent.index.layout;

/**
 * Builds a one-key {@link KeyStorage} and fixes the key order for that storage.
 * {@link io.dsal.persistent.index.core.PersistentBPlusTree} uses this to create
 * initial leaf keys and to thread
 * the comparator through node operations; see {@link KeyStorage} for sequence
 * semantics.
 *
 * @param <K> key type
 * @see ArrayKeyStorageFactory
 * @see PackedByteKeyStorageFactory
 */
public interface KeyStorageFactory<K> {
    /**
     * Returns storage containing exactly {@code key}, ordered according to this
     * factory's comparator.
     *
     * @param key sole key in the new storage
     * @return non-empty key storage of size 1
     */
    KeyStorage<K> single(K key);
}
package io.dsal.versioned.index.core;

/**
 * Key–value pair exposed by {@link PersistentBPlusTree} iteration and range scans:
 * one entry in key order ({@link #key()} at index {@code i} pairs with {@link #val()}
 * in the same leaf).
 *
 * <p>Bounded scan: {@link PersistentBPlusTree#range(Object, Object) PersistentBPlusTree.range(K, K)}.</p>
 *
 * @param key key
 * @param val associated value
 * @param <K> key type
 * @param <V> value type
 * @see PersistentBPlusTree#iterator()
 */
public record KeyVal<K, V>(K key, V val) {
    /**
     * Package-private factory used when emitting pairs from tree walks.
     *
     * @param key key
     * @param val value
     * @return key–value pair
     */
    static <K, V> KeyVal<K, V> of(K key, V val) {
        return new KeyVal<>(key, val);
    }
}

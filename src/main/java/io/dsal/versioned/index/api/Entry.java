package io.dsal.versioned.index.api;

/**
 * A key-value pair exposed by ordered index read operations.
 *
 * @param <K> key type
 * @param <V> value type
 */
public interface Entry<K, V> {
    /**
     * Returns this entry's key.
     *
     * @return key for this entry
     */
    K key();

    /**
     * Returns this entry's value.
     *
     * @return value for this entry
     */
    V value();
}

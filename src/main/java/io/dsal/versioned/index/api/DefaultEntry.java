package io.dsal.versioned.index.api;

/**
 * Default immutable {@link Entry} implementation used by read APIs when no
 * custom mapping is requested.
 *
 * @param key entry key
 * @param value entry value
 * @param <K> key type
 * @param <V> value type
 */
public record DefaultEntry<K,V>(
        K key,
        V value
) implements Entry<K,V> {}

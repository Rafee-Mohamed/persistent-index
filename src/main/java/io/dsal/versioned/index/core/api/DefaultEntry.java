package io.dsal.versioned.index.core.api;

public record DefaultEntry<K,V>(
        K key,
        V value
) implements Entry<K,V> {}

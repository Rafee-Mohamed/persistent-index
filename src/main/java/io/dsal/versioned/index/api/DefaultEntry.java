package io.dsal.versioned.index.api;

public record DefaultEntry<K,V>(
        K key,
        V value
) implements Entry<K,V> {}

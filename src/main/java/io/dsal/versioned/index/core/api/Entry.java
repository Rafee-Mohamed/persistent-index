package io.dsal.versioned.index.core.api;

public interface Entry<K, V> {
    K key();
    V value();
}

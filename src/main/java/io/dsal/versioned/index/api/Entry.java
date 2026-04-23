package io.dsal.versioned.index.api;

public interface Entry<K, V> {
    K key();
    V value();
}

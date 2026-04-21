package io.dsal.versioned.index.core.api;

import java.util.Optional;

public interface Mutator<K, V> {

    Optional<V> put(K key, V value);

    Optional<V> remove(K key);
}

package io.dsal.persistent.index.core;

import io.dsal.persistent.index.layout.KeyStorage;
import io.dsal.persistent.index.layout.ValueStorage;

public sealed interface Node<K, V> {
    KeyStorage<K> keys();
    record Internal<K, V>(
            KeyStorage<K> keys,
            Children<K, V> children
    ) implements Node<K, V> {
    }

    record Leaf<K, V>(
            KeyStorage<K> keys,
            ValueStorage<V> values
    ) implements Node<K, V> {
    }
}

package io.dsal.versioned.index.persistent.core;

public record CommittedState<K, V>(Node<K, V> root, int size) {
    CommittedState() {
        this(null, 0);
    }
}

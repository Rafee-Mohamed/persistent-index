package io.dsal.versioned.index.persistent.core;

import io.dsal.versioned.index.core.Node;

public record CommittedState<K, V>(Node<K, V> root, int size) {
}

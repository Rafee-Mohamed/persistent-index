package io.dsal.persistent.index.core;

public sealed interface DeleteResult<K, V> {
    default V removed() { return null; }

    record NotFound<K, V>() implements DeleteResult<K, V> {}

    record NoShrink<K, V>(
            Node<K, V> node,
            V removed
    ) implements DeleteResult<K, V> {}

    record Shrink<K, V>(
            Node<K, V> node,
            V removed
    ) implements DeleteResult<K, V> {}
}

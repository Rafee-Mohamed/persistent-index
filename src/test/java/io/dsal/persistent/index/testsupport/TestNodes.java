package io.dsal.persistent.index.testsupport;

import io.dsal.persistent.index.core.Node;
import io.dsal.persistent.index.layout.KeyStorageFactory;
import io.dsal.persistent.index.layout.ValueStorage;

/**
 * Minimal {@link Node.Leaf} builders for tests (e.g. {@code Children} slot payloads).
 */
public final class TestNodes {

    public static <K, V> Node.Leaf<K, V> leaf(KeyStorageFactory<K> ksf, K key, V value) {
        return new Node.Leaf<>(ksf.single(key), ValueStorage.of(value));
    }

    private TestNodes() {}
}

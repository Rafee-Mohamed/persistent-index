package io.dsal.versioned.index.core.api;

public interface TxnHandle<K, V> extends ReadView<K, V>, Mutator<K, V> {
}

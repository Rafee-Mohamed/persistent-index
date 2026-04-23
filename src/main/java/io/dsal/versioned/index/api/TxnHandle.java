package io.dsal.versioned.index.api;

public interface TxnHandle<K, V> extends ReadView<K, V>, Mutator<K, V> {
}

package io.dsal.versioned.index.api;

@FunctionalInterface
public interface TxnAction<K, V, E extends Exception> {
    void apply(TxnHandle<K, V> th) throws E;
}

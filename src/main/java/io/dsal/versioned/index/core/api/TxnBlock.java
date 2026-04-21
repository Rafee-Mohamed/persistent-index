package io.dsal.versioned.index.core.api;

@FunctionalInterface
public interface TxnBlock<K, V, E extends Exception> {
    void apply(TxnHandle<K, V> th) throws E;
}

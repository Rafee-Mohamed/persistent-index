package io.dsal.versioned.index.api;

@FunctionalInterface
public interface TxnBlock<K, V, R, E extends Exception> {
    R apply(TxnHandle<K, V> th) throws E;
}

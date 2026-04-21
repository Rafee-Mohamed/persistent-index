package io.dsal.versioned.index.core.api;

public interface Txn<K, V> extends TxnHandle<K, V> {
    void commit();
}

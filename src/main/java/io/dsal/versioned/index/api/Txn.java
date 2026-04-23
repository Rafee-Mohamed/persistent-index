package io.dsal.versioned.index.api;

public interface Txn<K, V> extends TxnHandle<K, V> {
    void commit();
}

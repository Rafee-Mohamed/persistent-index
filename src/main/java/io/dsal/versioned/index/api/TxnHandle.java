package io.dsal.versioned.index.api;

public interface TxnHandle<K, V> extends ReadView<K, V>, Mutator<K, V> {
    // snapshot as of this txn start - this snapshot won't be changed throughout the lifetime of txn
    Snapshot<K, V> snapshot();
}

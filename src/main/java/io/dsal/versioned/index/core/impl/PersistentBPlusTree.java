package io.dsal.versioned.index.core.impl;

import io.dsal.versioned.index.core.api.OrderedVersionedIndex;
import io.dsal.versioned.index.core.api.Snapshot;
import io.dsal.versioned.index.core.api.Txn;

public class PersistentBPlusTree<K, V> implements OrderedVersionedIndex<K, V> {
    @Override
    public Snapshot<K, V> snapshot() {
        return new PersistentBPlusTreeSnapshot<>();
    }

    @Override
    public Txn<K, V> txn() {
        return new PersistentBPlusTreeTxn<>();
    }
}

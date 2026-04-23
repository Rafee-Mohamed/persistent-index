package io.dsal.versioned.index.persistent;

import io.dsal.versioned.index.api.OrderedVersionedIndex;
import io.dsal.versioned.index.api.Snapshot;
import io.dsal.versioned.index.api.Txn;
import io.dsal.versioned.index.persistent.core.PersistentBPlusTreeSnapshot;
import io.dsal.versioned.index.persistent.core.PersistentBPlusTreeTxn;

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

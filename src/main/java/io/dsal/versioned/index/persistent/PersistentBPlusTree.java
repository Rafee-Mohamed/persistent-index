package io.dsal.versioned.index.persistent;

import io.dsal.versioned.index.api.OrderedVersionedIndex;
import io.dsal.versioned.index.api.Snapshot;
import io.dsal.versioned.index.api.Txn;
import io.dsal.versioned.index.persistent.core.PersistentBPlusTreeSnapshot;
import io.dsal.versioned.index.persistent.core.PersistentBPlusTreeTxn;
import io.dsal.versioned.index.persistent.core.ReadQuery;
import io.dsal.versioned.index.persistent.core.StateCommitter;
import io.dsal.versioned.index.persistent.layout.KeyStorageFactory;

public class PersistentBPlusTree<K, V> implements OrderedVersionedIndex<K, V> {

    private final KeyStorageFactory<K> ksf;
    private final StateCommitter<K, V> committer;
    private final int maxKeys;
    private final int minKeys;
    private final ReadQuery<K, V> query;

    public PersistentBPlusTree(int maxKeys, KeyStorageFactory<K> ksf) {
        this.maxKeys = maxKeys;
        this.minKeys = maxKeys / 2;
        this.ksf = ksf;
        this.committer = new StateCommitter<>();
        this.query = new ReadQuery<>();
    }

    @Override
    public Snapshot<K, V> snapshot() {
        return new PersistentBPlusTreeSnapshot<>(committer.committed(), query);
    }

    @Override
    public Txn<K, V> txn() {
        return new PersistentBPlusTreeTxn<>(committer, ksf, query, maxKeys, minKeys);
    }
}

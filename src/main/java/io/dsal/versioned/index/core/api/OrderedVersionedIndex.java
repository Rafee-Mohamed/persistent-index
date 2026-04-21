package io.dsal.versioned.index.core.api;

public interface OrderedVersionedIndex<K, V> extends ReadView<K, V>, Mutator<K, V> {

    Snapshot<K, V> snapshot();

    Txn<K, V> txn();

    default <E extends Exception> void txn(TxnBlock<K, V, E> block) throws E {
        var txn = txn();
        block.apply(txn);
        txn.commit();
    }
}

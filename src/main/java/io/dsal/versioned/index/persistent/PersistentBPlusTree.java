package io.dsal.versioned.index.persistent;

import io.dsal.versioned.index.api.OrderedVersionedIndex;
import io.dsal.versioned.index.api.Snapshot;
import io.dsal.versioned.index.api.Txn;
import io.dsal.versioned.index.persistent.core.PersistentBPlusTreeSnapshot;
import io.dsal.versioned.index.persistent.core.PersistentBPlusTreeTxn;
import io.dsal.versioned.index.persistent.core.ReadQuery;
import io.dsal.versioned.index.persistent.core.StateCommitter;
import io.dsal.versioned.index.persistent.layout.KeyStorageFactory;

/**
 * Persistent copy-on-write B+ tree implementation of {@link OrderedVersionedIndex}.
 *
 * <p>This class provides ordered index semantics with snapshot-isolated reads and
 * transactional writes:
 * <ul>
 *   <li>{@link #snapshot()} returns a stable view of the latest committed state.</li>
 *   <li>{@link #txn()} returns a mutable transaction with commit-once lifecycle.</li>
 * </ul>
 *
 * <p>Read methods inherited from {@link OrderedVersionedIndex} are evaluated on
 * committed snapshots. Single-operation mutations ({@code put}/{@code remove})
 * are provided by interface defaults and execute as one-operation transactions.
 *
 * <h2>Structure</h2>
 *
 * <pre>
 *   Internal node:
 *     keys:     [ K0 | K1 | K2 ]
 *     children: [ C0   C1   C2   C3 ]     (children.size() == keys.size() + 1)
 *
 *   Leaf node:
 *     keys:   [ K0 | K1 | K2 ]
 *     values: [ V0 | V1 | V2 ]            (values.size() == keys.size())
 * </pre>
 *
 * <p>Internal nodes hold separator keys only; values exist only in leaves. Descent
 * uses upper-bound search on separator keys to select the child subtree.
 *
 * <h2>Invariants</h2>
 *
 * <ul>
 *   <li>Keys are strictly sorted according to the {@link io.dsal.versioned.index.persistent.layout.KeyStorage} comparator</li>
 *   <li>All leaves are at equal depth</li>
 *   <li>Non-root nodes have at least {@code minKeys == maxKeys / 2} keys</li>
 *   <li>All nodes have at most {@code maxKeys} keys; overflow triggers a split</li>
 * </ul>
 *
 * <h2>Concurrency</h2>
 *
 * <p>Intended for single-writer, multiple-reader (SWMR) workloads. Concurrent
 * readers via snapshots require no locks; concurrent write transactions require
 * external synchronization.
 *
 * @param <K> key type
 * @param <V> value type
 */
public class PersistentBPlusTree<K, V> implements OrderedVersionedIndex<K, V> {

    /**
     * Minimum valid value for {@code maxKeys}. A node with fewer than two keys
     * cannot split into two non-empty halves, so values below this are capped.
     */
    private static final int MIN_POSSIBLE_KEYS = 2;

    /** Factory for key storage instances and key ordering used when creating new nodes. */
    private final KeyStorageFactory<K> ksf;
    /** Holds the current committed state and publishes new states on commit. */
    private final StateCommitter<K, V> committer;
    /**
     * Maximum keys per node; insertion splits when a node would exceed this count.
     * Always {@code >= MIN_POSSIBLE_KEYS}.
     */
    private final int maxKeys;
    /** Minimum keys required in a non-root node; {@code maxKeys / 2}. */
    private final int minKeys;
    /** Shared read engine used by both snapshots and transactions. */
    private final ReadQuery<K, V> query;

    /**
     * Creates an empty index.
     *
     * <p>Valid values for {@code maxKeys} are {@code >= 2}. Values below
     * {@value #MIN_POSSIBLE_KEYS} are silently capped to {@value #MIN_POSSIBLE_KEYS}
     * because a node with fewer than two keys cannot split into two non-empty halves.
     *
     * @param maxKeys maximum number of keys per node before split; capped to
     *                {@value #MIN_POSSIBLE_KEYS} if smaller
     * @param ksf     factory used to create key-storage instances and key ordering behavior
     */
    public PersistentBPlusTree(int maxKeys, KeyStorageFactory<K> ksf) {
        this.maxKeys = Math.max(maxKeys, MIN_POSSIBLE_KEYS);
        this.minKeys = this.maxKeys / 2;
        this.ksf = ksf;
        this.committer = new StateCommitter<>();
        this.query = new ReadQuery<>();
    }

    /**
     * Returns a point-in-time snapshot of the latest committed state.
     *
     * @return immutable committed-state snapshot
     */
    @Override
    public Snapshot<K, V> snapshot() {
        return new PersistentBPlusTreeSnapshot<>(committer.committed(), query);
    }

    /**
     * Starts a new writable transaction from the latest committed state.
     *
     * @return new transaction handle
     */
    @Override
    public Txn<K, V> txn() {
        return new PersistentBPlusTreeTxn<>(committer, ksf, query, maxKeys, minKeys);
    }
}

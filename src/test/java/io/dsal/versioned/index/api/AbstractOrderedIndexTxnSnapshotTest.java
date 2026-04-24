package io.dsal.versioned.index.api;

import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Abstract snapshot-isolation suite for any {@link OrderedVersionedIndex} implementation.
 *
 * <p>Covers snapshot semantics around multi-operation transactions:
 * <ul>
 *   <li>Pre-txn snapshots are unaffected by the txn commit.</li>
 *   <li>{@link TxnHandle#snapshot()} captures committed state at txn start only.</li>
 *   <li>{@link TxnHandle#snapshot()} does not reflect the txn's own mutations.</li>
 *   <li>Snapshots taken before and after a multi-op txn see their respective committed versions.</li>
 *   <li>Post-commit snapshots see all mutations atomically.</li>
 *   <li>Iterators from snapshots are unaffected by subsequent txn commits.</li>
 *   <li>Range queries on snapshots see only their version.</li>
 *   <li>Chains of multiple txns produce correctly versioned snapshots at each step.</li>
 * </ul>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class AbstractOrderedIndexTxnSnapshotTest {

    protected abstract Stream<Supplier<OrderedVersionedIndex<Integer, String>>> indexFactories();

    // -------------------------------------------------------------------------
    // Collect helpers
    // -------------------------------------------------------------------------

    private static List<Map.Entry<Integer, String>> collectForEach(
            ReadView<Integer, String> view, Direction dir) {
        var out = new ArrayList<Map.Entry<Integer, String>>();
        view.forEach(dir, (k, v) -> out.add(Map.entry(k, v)));
        return out;
    }

    private static List<Map.Entry<Integer, String>> collectIter(
            ReadView<Integer, String> view, Direction dir) {
        var out = new ArrayList<Map.Entry<Integer, String>>();
        Iterator<? extends Entry<Integer, String>> it = view.iterator(dir);
        it.forEachRemaining(e -> out.add(Map.entry(e.key(), e.value())));
        return out;
    }

    private static List<Map.Entry<Integer, String>> collectIterRange(
            ReadView<Integer, String> view, Direction dir, Range<Integer> range) {
        var out = new ArrayList<Map.Entry<Integer, String>>();
        Iterator<? extends Entry<Integer, String>> it = view.iterator(dir, range);
        it.forEachRemaining(e -> out.add(Map.entry(e.key(), e.value())));
        return out;
    }

    private static List<Map.Entry<Integer, String>> sorted(TreeMap<Integer, String> map) {
        return new ArrayList<>(map.entrySet());
    }

    // -------------------------------------------------------------------------
    // Pre-txn snapshot unaffected by commit
    // -------------------------------------------------------------------------

    @ParameterizedTest
    @MethodSource("indexFactories")
    void preTxnSnapshotUnaffectedByMultiOpTxnCommit(Supplier<OrderedVersionedIndex<Integer, String>> factory) {
        var index = factory.get();
        for (int k = 1; k <= 5; k++) index.put(k, "v" + k);

        var snap = index.snapshot();

        // multi-op txn: add, overwrite, remove
        var txn = index.txn();
        for (int k = 6; k <= 15; k++) txn.put(k, "v" + k);
        for (int k = 1; k <= 3; k++) txn.remove(k);
        txn.put(1, "updated1");
        txn.commit();

        // pre-txn snapshot unchanged
        assertThat(snap.size()).isEqualTo(5);
        for (int k = 1; k <= 5; k++) assertThat(snap.get(k)).hasValue("v" + k);
        for (int k = 6; k <= 15; k++) assertThat(snap.get(k)).isEmpty();

        var actual = collectForEach(snap, Direction.ASC);
        var expected = new ArrayList<Map.Entry<Integer, String>>();
        for (int k = 1; k <= 5; k++) expected.add(Map.entry(k, "v" + k));
        assertThat(actual).isEqualTo(expected);
    }

    @ParameterizedTest
    @MethodSource("indexFactories")
    void emptyPreTxnSnapshotRemainsEmptyAfterLargeTxnCommit(Supplier<OrderedVersionedIndex<Integer, String>> factory) {
        var index = factory.get();
        var snap = index.snapshot(); // empty snapshot

        var txn = index.txn();
        for (int k = 1; k <= 30; k++) txn.put(k, "v" + k);
        txn.commit();

        assertThat(snap.size()).isZero();
        assertThat(snap.get(1)).isEmpty();
        assertThat(collectForEach(snap, Direction.ASC)).isEmpty();
    }

    // -------------------------------------------------------------------------
    // txn.snapshot() matches pre-txn index state
    // -------------------------------------------------------------------------

    @ParameterizedTest
    @MethodSource("indexFactories")
    void txnBaseSnapshotMatchesPreTxnState(Supplier<OrderedVersionedIndex<Integer, String>> factory) {
        var index = factory.get();
        for (int k = 1; k <= 10; k++) index.put(k, "v" + k);

        var snapBefore = index.snapshot();
        var txn = index.txn();
        var txnSnap = txn.snapshot();

        // txn.snapshot() content equals pre-txn index snapshot
        assertThat(collectForEach(txnSnap, Direction.ASC))
                .isEqualTo(collectForEach(snapBefore, Direction.ASC));
        assertThat(txnSnap.size()).isEqualTo(snapBefore.size());

        txn.commit();
    }

    @ParameterizedTest
    @MethodSource("indexFactories")
    void txnBaseSnapshotMatchesPreTxnStateAfterManyCommits(Supplier<OrderedVersionedIndex<Integer, String>> factory) {
        var index = factory.get();

        // Do several commits to advance the version
        for (int round = 0; round < 5; round++) {
            var txnSetup = index.txn();
            for (int k = round * 10 + 1; k <= round * 10 + 10; k++) txnSetup.put(k, "v" + k);
            txnSetup.commit();
        }

        var snapBefore = index.snapshot();
        var txn = index.txn();
        var txnSnap = txn.snapshot();

        assertThat(txnSnap.size()).isEqualTo(snapBefore.size());
        assertThat(collectForEach(txnSnap, Direction.ASC))
                .isEqualTo(collectForEach(snapBefore, Direction.ASC));

        txn.commit();
    }

    // -------------------------------------------------------------------------
    // txn.snapshot() does not see txn-local mutations
    // -------------------------------------------------------------------------

    @ParameterizedTest
    @MethodSource("indexFactories")
    void txnBaseSnapshotDoesNotSeeTxnLocalMutations(Supplier<OrderedVersionedIndex<Integer, String>> factory) {
        var index = factory.get();
        for (int k = 1; k <= 5; k++) index.put(k, "v" + k);

        var txn = index.txn();
        var txnSnap = txn.snapshot();

        // Apply mutations through txn
        txn.put(6, "v6");
        txn.put(7, "v7");
        txn.remove(1);
        txn.put(2, "updated2");

        // txnSnap still shows pre-txn state, not txn mutations
        assertThat(txnSnap.size()).isEqualTo(5);
        assertThat(txnSnap.get(6)).isEmpty();
        assertThat(txnSnap.get(7)).isEmpty();
        assertThat(txnSnap.get(1)).hasValue("v1");
        assertThat(txnSnap.get(2)).hasValue("v2");

        txn.commit();
    }

    @ParameterizedTest
    @MethodSource("indexFactories")
    void txnBaseSnapshotStableAcrossAllTxnMutationTypes(Supplier<OrderedVersionedIndex<Integer, String>> factory) {
        var index = factory.get();
        for (int k = 1; k <= 20; k++) index.put(k, "v" + k);

        var txn = index.txn();
        var txnSnap = txn.snapshot();
        var preSnapContent = collectForEach(txnSnap, Direction.ASC);

        // Lots of mutations: overwrites, removes, new puts, put-then-remove
        for (int k = 1; k <= 10; k++) txn.put(k, "new" + k);
        for (int k = 11; k <= 15; k++) txn.remove(k);
        for (int k = 21; k <= 30; k++) txn.put(k, "extra" + k);
        txn.put(50, "v50");
        txn.remove(50);

        // txnSnap still shows original 20 keys
        assertThat(txnSnap.size()).isEqualTo(20);
        assertThat(collectForEach(txnSnap, Direction.ASC)).isEqualTo(preSnapContent);

        txn.commit();
    }

    // -------------------------------------------------------------------------
    // txn.snapshot() stable after txn commits
    // -------------------------------------------------------------------------

    @ParameterizedTest
    @MethodSource("indexFactories")
    void txnBaseSnapshotStillUsableAfterTxnCommit(Supplier<OrderedVersionedIndex<Integer, String>> factory) {
        var index = factory.get();
        for (int k = 1; k <= 8; k++) index.put(k, "v" + k);

        var txn = index.txn();
        var txnSnap = txn.snapshot(); // capture before commit
        txn.put(9, "v9");
        txn.put(10, "v10");
        txn.remove(1);
        txn.commit();

        // txnSnap captured pre-commit state: keys 1-8, no key 9 or 10
        assertThat(txnSnap.size()).isEqualTo(8);
        assertThat(txnSnap.get(1)).hasValue("v1");
        assertThat(txnSnap.get(9)).isEmpty();
        assertThat(txnSnap.get(10)).isEmpty();

        // Further commits do not affect txnSnap
        for (int k = 11; k <= 20; k++) index.put(k, "v" + k);

        assertThat(txnSnap.size()).isEqualTo(8);
        for (int k = 11; k <= 20; k++) assertThat(txnSnap.get(k)).isEmpty();
    }

    // -------------------------------------------------------------------------
    // Post-commit snapshot sees all mutations atomically
    // -------------------------------------------------------------------------

    @ParameterizedTest
    @MethodSource("indexFactories")
    void postCommitSnapshotSeesAllTxnMutationsAtomically(Supplier<OrderedVersionedIndex<Integer, String>> factory) {
        var index = factory.get();
        for (int k = 1; k <= 10; k++) index.put(k, "old" + k);

        var txn = index.txn();
        for (int k = 1; k <= 5; k++) txn.put(k, "new" + k);
        for (int k = 6; k <= 10; k++) txn.remove(k);
        for (int k = 11; k <= 15; k++) txn.put(k, "extra" + k);
        txn.commit();

        var postSnap = index.snapshot();
        assertThat(postSnap.size()).isEqualTo(10); // 5 updated + 5 new

        for (int k = 1; k <= 5; k++) assertThat(postSnap.get(k)).hasValue("new" + k);
        for (int k = 6; k <= 10; k++) assertThat(postSnap.get(k)).isEmpty();
        for (int k = 11; k <= 15; k++) assertThat(postSnap.get(k)).hasValue("extra" + k);

        var expected = new ArrayList<Map.Entry<Integer, String>>();
        for (int k = 1; k <= 5; k++) expected.add(Map.entry(k, "new" + k));
        for (int k = 11; k <= 15; k++) expected.add(Map.entry(k, "extra" + k));
        assertThat(collectForEach(postSnap, Direction.ASC)).isEqualTo(expected);
    }

    // -------------------------------------------------------------------------
    // Two snapshots bracketing a multi-op txn
    // -------------------------------------------------------------------------

    @ParameterizedTest
    @MethodSource("indexFactories")
    void twoSnapshotsBracketingMultiOpTxnHaveCorrectSizesAndContents(Supplier<OrderedVersionedIndex<Integer, String>> factory) {
        var index = factory.get();
        for (int k = 1; k <= 5; k++) index.put(k, "v" + k);

        var snapBefore = index.snapshot();

        var txn = index.txn();
        for (int k = 6; k <= 10; k++) txn.put(k, "v" + k);
        for (int k = 1; k <= 2; k++) txn.remove(k);
        txn.commit();

        var snapAfter = index.snapshot();

        assertThat(snapBefore.size()).isEqualTo(5);
        assertThat(snapAfter.size()).isEqualTo(8); // 5 - 2 + 5 = 8

        for (int k = 1; k <= 5; k++) assertThat(snapBefore.get(k)).hasValue("v" + k);
        for (int k = 6; k <= 10; k++) assertThat(snapBefore.get(k)).isEmpty();

        assertThat(snapAfter.get(1)).isEmpty();
        assertThat(snapAfter.get(2)).isEmpty();
        for (int k = 3; k <= 10; k++) assertThat(snapAfter.get(k)).hasValue("v" + k);
    }

    @ParameterizedTest
    @MethodSource("indexFactories")
    void twoSnapshotsBracketingLargeTxnAreCompletelyIndependent(Supplier<OrderedVersionedIndex<Integer, String>> factory) {
        var index = factory.get();
        for (int k = 1; k <= 20; k++) index.put(k, "before" + k);

        var snapBefore = index.snapshot();

        var txn = index.txn();
        // Replace all 20 keys + add 20 more
        for (int k = 1; k <= 20; k++) txn.put(k, "after" + k);
        for (int k = 21; k <= 40; k++) txn.put(k, "after" + k);
        txn.commit();

        var snapAfter = index.snapshot();

        assertThat(snapBefore.size()).isEqualTo(20);
        assertThat(snapAfter.size()).isEqualTo(40);

        for (int k = 1; k <= 20; k++) {
            assertThat(snapBefore.get(k)).hasValue("before" + k);
            assertThat(snapAfter.get(k)).hasValue("after" + k);
        }
        for (int k = 21; k <= 40; k++) {
            assertThat(snapBefore.get(k)).isEmpty();
            assertThat(snapAfter.get(k)).hasValue("after" + k);
        }
    }

    // -------------------------------------------------------------------------
    // Snapshot iterator stable across subsequent txn commits
    // -------------------------------------------------------------------------

    @ParameterizedTest
    @MethodSource("indexFactories")
    void snapshotAscIteratorUnaffectedBySubsequentTxnCommit(Supplier<OrderedVersionedIndex<Integer, String>> factory) {
        var index = factory.get();
        for (int k = 1; k <= 6; k++) index.put(k, "v" + k);

        var snap = index.snapshot();
        var it = snap.iterator(Direction.ASC);

        // Commit a txn while the iterator is "in flight"
        var txn = index.txn();
        for (int k = 7; k <= 20; k++) txn.put(k, "extra" + k);
        for (int k = 1; k <= 3; k++) txn.remove(k);
        txn.commit();

        // Iterator should yield only the 6 entries from the snapshot
        var actual = new ArrayList<Map.Entry<Integer, String>>();
        it.forEachRemaining(e -> actual.add(Map.entry(e.key(), e.value())));

        var expected = new ArrayList<Map.Entry<Integer, String>>();
        for (int k = 1; k <= 6; k++) expected.add(Map.entry(k, "v" + k));
        assertThat(actual).isEqualTo(expected);
    }

    @ParameterizedTest
    @MethodSource("indexFactories")
    void snapshotDescIteratorUnaffectedBySubsequentTxnCommit(Supplier<OrderedVersionedIndex<Integer, String>> factory) {
        var index = factory.get();
        for (int k = 1; k <= 6; k++) index.put(k, "v" + k);

        var snap = index.snapshot();
        var it = snap.iterator(Direction.DESC);

        var txn = index.txn();
        for (int k = 7; k <= 15; k++) txn.put(k, "extra" + k);
        txn.commit();

        var actual = new ArrayList<Map.Entry<Integer, String>>();
        it.forEachRemaining(e -> actual.add(Map.entry(e.key(), e.value())));

        var expected = new ArrayList<Map.Entry<Integer, String>>();
        for (int k = 6; k >= 1; k--) expected.add(Map.entry(k, "v" + k));
        assertThat(actual).isEqualTo(expected);
    }

    // -------------------------------------------------------------------------
    // Snapshot range queries see only their version
    // -------------------------------------------------------------------------

    @ParameterizedTest
    @MethodSource("indexFactories")
    void txnBaseSnapshotRangeQuerySeesOnlyPreTxnState(Supplier<OrderedVersionedIndex<Integer, String>> factory) {
        var index = factory.get();
        for (int k = 1; k <= 15; k++) index.put(k, "v" + k);

        var txn = index.txn();
        var txnSnap = txn.snapshot();

        // mutations within txn
        txn.put(5, "updated5");
        txn.remove(8);
        txn.put(16, "v16");

        // txnSnap range query still sees original state
        var rangeResult = collectIterRange(txnSnap, Direction.ASC, Range.closed(4, 10));
        assertThat(rangeResult).containsExactly(
                Map.entry(4, "v4"), Map.entry(5, "v5"), Map.entry(6, "v6"),
                Map.entry(7, "v7"), Map.entry(8, "v8"), Map.entry(9, "v9"),
                Map.entry(10, "v10")
        );

        txn.commit();
    }

    @ParameterizedTest
    @MethodSource("indexFactories")
    void preTxnSnapshotAllFourRangeTypesUnaffectedByTxnCommit(Supplier<OrderedVersionedIndex<Integer, String>> factory) {
        var index = factory.get();
        for (int k = 1; k <= 20; k++) index.put(k, "v" + k);

        var snap = index.snapshot();

        // multi-op txn removes keys 5-10 which fall inside all range queries below
        var txn = index.txn();
        for (int k = 5; k <= 10; k++) txn.remove(k);
        for (int k = 21; k <= 25; k++) txn.put(k, "v" + k);
        txn.commit();

        // snap should still see original 5-10 in all range types
        assertThat(collectIterRange(snap, Direction.ASC, Range.closed(4, 11))).containsExactly(
                Map.entry(4, "v4"), Map.entry(5, "v5"), Map.entry(6, "v6"),
                Map.entry(7, "v7"), Map.entry(8, "v8"), Map.entry(9, "v9"),
                Map.entry(10, "v10"), Map.entry(11, "v11")
        );
        assertThat(collectIterRange(snap, Direction.ASC, Range.open(4, 11))).containsExactly(
                Map.entry(5, "v5"), Map.entry(6, "v6"), Map.entry(7, "v7"),
                Map.entry(8, "v8"), Map.entry(9, "v9"), Map.entry(10, "v10")
        );
        assertThat(collectIterRange(snap, Direction.ASC, Range.closedOpen(4, 11))).containsExactly(
                Map.entry(4, "v4"), Map.entry(5, "v5"), Map.entry(6, "v6"),
                Map.entry(7, "v7"), Map.entry(8, "v8"), Map.entry(9, "v9"),
                Map.entry(10, "v10")
        );
        assertThat(collectIterRange(snap, Direction.ASC, Range.openClosed(4, 11))).containsExactly(
                Map.entry(5, "v5"), Map.entry(6, "v6"), Map.entry(7, "v7"),
                Map.entry(8, "v8"), Map.entry(9, "v9"), Map.entry(10, "v10"),
                Map.entry(11, "v11")
        );
    }

    // -------------------------------------------------------------------------
    // Chain of txns: each snapshot shows correct version
    // -------------------------------------------------------------------------

    @ParameterizedTest
    @MethodSource("indexFactories")
    void chainOfTxnsEachSnapshotShowsCorrectVersion(Supplier<OrderedVersionedIndex<Integer, String>> factory) {
        var index = factory.get();
        var snapshots = new ArrayList<Snapshot<Integer, String>>();
        var expectedSizes = new ArrayList<Integer>();
        int expectedSize = 0;

        // 8 txns, each adds 5 keys and removes 2 (net +3 per txn)
        for (int round = 0; round < 8; round++) {
            snapshots.add(index.snapshot());
            expectedSizes.add(expectedSize);

            var txn = index.txn();
            for (int i = 0; i < 5; i++) txn.put(round * 100 + i, "r" + round + "k" + i);
            if (round >= 1) {
                txn.remove((round - 1) * 100); // remove first key of previous round
                txn.remove((round - 1) * 100 + 1);
                expectedSize -= 2;
            }
            expectedSize += 5;
            txn.commit();
        }

        // final snapshot
        snapshots.add(index.snapshot());
        expectedSizes.add(expectedSize);

        for (int i = 0; i < snapshots.size(); i++) {
            assertThat(snapshots.get(i).size()).as("snapshot[%d].size", i)
                    .isEqualTo(expectedSizes.get(i));
        }
    }

    @ParameterizedTest
    @MethodSource("indexFactories")
    void manySmallTxnsSnapshotVersionsAreMonotonicallyIncreasing(Supplier<OrderedVersionedIndex<Integer, String>> factory) {
        var index = factory.get();

        var snapBefore = index.snapshot();
        assertThat(snapBefore.size()).isZero();

        for (int k = 1; k <= 10; k++) {
            index.put(k, "v" + k); // single-op txn each
        }

        var snapAfter = index.snapshot();
        assertThat(snapAfter.size()).isEqualTo(10);

        // snapBefore still sees empty
        assertThat(snapBefore.size()).isZero();
        assertThat(snapBefore.get(1)).isEmpty();
    }

    // -------------------------------------------------------------------------
    // Large multi-op txn: post-commit snapshot full scan matches oracle
    // -------------------------------------------------------------------------

    @ParameterizedTest
    @MethodSource("indexFactories")
    void largeTxnPostCommitSnapshotFullScanMatchesOracle(Supplier<OrderedVersionedIndex<Integer, String>> factory) {
        var index = factory.get();
        // pre-load 30 keys
        for (int k = 1; k <= 30; k++) index.put(k, "pre" + k);

        var oracle = new TreeMap<Integer, String>();
        for (int k = 1; k <= 30; k++) oracle.put(k, "pre" + k);

        // large txn: overwrite odds, remove evens, add new keys
        var txn = index.txn();
        for (int k = 1; k <= 30; k += 2) {
            txn.put(k, "upd" + k);
            oracle.put(k, "upd" + k);
        }
        for (int k = 2; k <= 30; k += 2) {
            txn.remove(k);
            oracle.remove(k);
        }
        for (int k = 31; k <= 50; k++) {
            txn.put(k, "new" + k);
            oracle.put(k, "new" + k);
        }
        txn.commit();

        var postSnap = index.snapshot();
        var expected = sorted(oracle);

        assertThat(collectForEach(postSnap, Direction.ASC)).isEqualTo(expected);
        assertThat(collectIter(postSnap, Direction.ASC)).isEqualTo(expected);

        var expectedDesc = new ArrayList<>(expected);
        Collections.reverse(expectedDesc);
        assertThat(collectForEach(postSnap, Direction.DESC)).isEqualTo(expectedDesc);
        assertThat(collectIter(postSnap, Direction.DESC)).isEqualTo(expectedDesc);

        assertThat(postSnap.size()).isEqualTo(oracle.size());
    }
}

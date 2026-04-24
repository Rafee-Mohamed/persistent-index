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
 * Abstract txn scenario suite for any {@link OrderedVersionedIndex} implementation.
 *
 * <p>Covers the full write-then-read surface of {@link TxnHandle}:
 * <ul>
 *   <li>Read-your-own-writes via every {@link ReadView} API after each mutation type.</li>
 *   <li>Overwrite, put-then-remove, and remove-then-reinsert within a single txn.</li>
 *   <li>Size tracking across puts, overwrites, and removes.</li>
 *   <li>forEach and iterator agreement on both directions within the txn.</li>
 *   <li>All four range types via forEach and iterator within the txn.</li>
 *   <li>Cascading split (many puts) and cascading merge (many removes) correctness.</li>
 *   <li>Interleaved mutation sequence with read verification at each step.</li>
 *   <li>Isolation: mutations invisible to the index before commit, all visible after.</li>
 * </ul>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class AbstractOrderedIndexTxnTest {

    protected abstract Stream<Supplier<OrderedVersionedIndex<Integer, String>>> indexFactories();

    // -------------------------------------------------------------------------
    // Collect helpers for ReadView
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

    private static List<Map.Entry<Integer, String>> collectForEachRange(
            ReadView<Integer, String> view, Direction dir, Range<Integer> range) {
        var out = new ArrayList<Map.Entry<Integer, String>>();
        view.forEach(dir, range, (k, v) -> out.add(Map.entry(k, v)));
        return out;
    }

    private static List<Map.Entry<Integer, String>> collectIterRange(
            ReadView<Integer, String> view, Direction dir, Range<Integer> range) {
        var out = new ArrayList<Map.Entry<Integer, String>>();
        Iterator<? extends Entry<Integer, String>> it = view.iterator(dir, range);
        it.forEachRemaining(e -> out.add(Map.entry(e.key(), e.value())));
        return out;
    }

    /** Asserts all full-scan ReadView APIs (forEach + iterator, ASC + DESC) match expectedAsc. */
    private static void assertAllFullScanApisMatch(
            ReadView<Integer, String> view, List<Map.Entry<Integer, String>> expectedAsc) {
        var expectedDesc = new ArrayList<>(expectedAsc);
        Collections.reverse(expectedDesc);

        assertThat(view.size()).as("size").isEqualTo(expectedAsc.size());
        assertThat(collectForEach(view, Direction.ASC)).as("forEach(ASC)").isEqualTo(expectedAsc);
        assertThat(collectIter(view, Direction.ASC)).as("iterator(ASC)").isEqualTo(expectedAsc);
        assertThat(collectForEach(view, Direction.DESC)).as("forEach(DESC)").isEqualTo(expectedDesc);
        assertThat(collectIter(view, Direction.DESC)).as("iterator(DESC)").isEqualTo(expectedDesc);
    }

    /** Asserts all range ReadView APIs (forEach + iterator, ASC + DESC) match expectedAsc. */
    private static void assertAllRangeApisMatch(
            ReadView<Integer, String> view, Range<Integer> range,
            List<Map.Entry<Integer, String>> expectedAsc) {
        var expectedDesc = new ArrayList<>(expectedAsc);
        Collections.reverse(expectedDesc);

        assertThat(collectForEachRange(view, Direction.ASC, range))
                .as("forEach(ASC, %s)", range).isEqualTo(expectedAsc);
        assertThat(collectIterRange(view, Direction.ASC, range))
                .as("iterator(ASC, %s)", range).isEqualTo(expectedAsc);
        assertThat(collectForEachRange(view, Direction.DESC, range))
                .as("forEach(DESC, %s)", range).isEqualTo(expectedDesc);
        assertThat(collectIterRange(view, Direction.DESC, range))
                .as("iterator(DESC, %s)", range).isEqualTo(expectedDesc);
    }

    private static List<Map.Entry<Integer, String>> sorted(TreeMap<Integer, String> map) {
        return new ArrayList<>(map.entrySet());
    }

    // -------------------------------------------------------------------------
    // Read-your-writes: single put
    // -------------------------------------------------------------------------

    @ParameterizedTest
    @MethodSource("indexFactories")
    void txnReadYourWritesViaAllApisAfterSinglePut(Supplier<OrderedVersionedIndex<Integer, String>> factory) {
        var index = factory.get();
        var txn = index.txn();
        txn.put(7, "v7");

        assertThat(txn.get(7)).hasValue("v7");
        assertThat(txn.contains(7)).isTrue();
        assertThat(txn.get(99)).isEmpty();
        assertThat(txn.contains(99)).isFalse();
        assertAllFullScanApisMatch(txn, List.of(Map.entry(7, "v7")));
        txn.commit();
    }

    // -------------------------------------------------------------------------
    // Read-your-writes: multiple puts
    // -------------------------------------------------------------------------

    @ParameterizedTest
    @MethodSource("indexFactories")
    void txnReadYourWritesViaAllApisAfterMultiplePuts(Supplier<OrderedVersionedIndex<Integer, String>> factory) {
        var index = factory.get();
        var txn = index.txn();
        var expected = new TreeMap<Integer, String>();
        for (int k = 1; k <= 10; k++) {
            txn.put(k, "v" + k);
            expected.put(k, "v" + k);
        }

        assertAllFullScanApisMatch(txn, sorted(expected));
        assertAllRangeApisMatch(txn, Range.closed(3, 7), List.of(
                Map.entry(3, "v3"), Map.entry(4, "v4"), Map.entry(5, "v5"),
                Map.entry(6, "v6"), Map.entry(7, "v7")
        ));
        txn.commit();
    }

    // -------------------------------------------------------------------------
    // Read-your-writes: removes from pre-loaded state
    // -------------------------------------------------------------------------

    @ParameterizedTest
    @MethodSource("indexFactories")
    void txnReadYourWritesViaAllApisAfterRemoves(Supplier<OrderedVersionedIndex<Integer, String>> factory) {
        var index = factory.get();
        for (int k = 1; k <= 8; k++) index.put(k, "v" + k);

        var txn = index.txn();
        txn.remove(2);
        txn.remove(5);
        txn.remove(7);

        assertThat(txn.get(2)).isEmpty();
        assertThat(txn.contains(2)).isFalse();
        assertThat(txn.get(5)).isEmpty();
        assertThat(txn.contains(5)).isFalse();
        assertThat(txn.get(1)).hasValue("v1");
        assertThat(txn.contains(8)).isTrue();

        var expected = new TreeMap<Integer, String>();
        for (int k : new int[]{1, 3, 4, 6, 8}) expected.put(k, "v" + k);
        assertAllFullScanApisMatch(txn, sorted(expected));
        txn.commit();
    }

    // -------------------------------------------------------------------------
    // Overwrite: second put replaces value immediately
    // -------------------------------------------------------------------------

    @ParameterizedTest
    @MethodSource("indexFactories")
    void txnOverwriteReadYourWritesNewValueImmediately(Supplier<OrderedVersionedIndex<Integer, String>> factory) {
        var index = factory.get();
        var txn = index.txn();
        txn.put(1, "first");

        assertThat(txn.get(1)).hasValue("first");
        assertThat(txn.size()).isEqualTo(1);

        txn.put(1, "second");

        assertThat(txn.get(1)).hasValue("second");
        assertThat(txn.size()).isEqualTo(1);
        assertAllFullScanApisMatch(txn, List.of(Map.entry(1, "second")));
        txn.commit();

        assertThat(index.get(1)).hasValue("second");
    }

    @ParameterizedTest
    @MethodSource("indexFactories")
    void txnOverwriteMultipleKeysReadsLatestValueEachTime(Supplier<OrderedVersionedIndex<Integer, String>> factory) {
        var index = factory.get();
        for (int k = 1; k <= 5; k++) index.put(k, "old" + k);

        var txn = index.txn();
        for (int k = 1; k <= 5; k++) txn.put(k, "new" + k);

        for (int k = 1; k <= 5; k++) {
            assertThat(txn.get(k)).hasValue("new" + k);
        }
        assertThat(txn.size()).isEqualTo(5);
        txn.commit();

        for (int k = 1; k <= 5; k++) {
            assertThat(index.get(k)).hasValue("new" + k);
        }
    }

    // -------------------------------------------------------------------------
    // Put then remove same key: net zero
    // -------------------------------------------------------------------------

    @ParameterizedTest
    @MethodSource("indexFactories")
    void txnPutThenRemoveSameKeyShowsAbsenceWithinTxnAndAfterCommit(Supplier<OrderedVersionedIndex<Integer, String>> factory) {
        var index = factory.get();
        var txn = index.txn();
        txn.put(42, "v42");
        txn.remove(42);

        assertThat(txn.get(42)).isEmpty();
        assertThat(txn.contains(42)).isFalse();
        assertThat(txn.size()).isZero();
        assertAllFullScanApisMatch(txn, List.of());
        txn.commit();

        assertThat(index.get(42)).isEmpty();
        assertThat(index.size()).isZero();
    }

    @ParameterizedTest
    @MethodSource("indexFactories")
    void txnPutThenRemoveMultipleKeysLeavesResidualCorrect(Supplier<OrderedVersionedIndex<Integer, String>> factory) {
        var index = factory.get();
        var txn = index.txn();
        for (int k = 1; k <= 10; k++) txn.put(k, "v" + k);
        for (int k = 1; k <= 5; k++) txn.remove(k); // remove first half

        var expected = new TreeMap<Integer, String>();
        for (int k = 6; k <= 10; k++) expected.put(k, "v" + k);

        assertAllFullScanApisMatch(txn, sorted(expected));
        txn.commit();

        assertThat(index.size()).isEqualTo(5);
        for (int k = 1; k <= 5; k++) assertThat(index.get(k)).isEmpty();
        for (int k = 6; k <= 10; k++) assertThat(index.get(k)).hasValue("v" + k);
    }

    // -------------------------------------------------------------------------
    // Remove then reinsert same key
    // -------------------------------------------------------------------------

    @ParameterizedTest
    @MethodSource("indexFactories")
    void txnRemoveThenReinsertSameKeyReadsNewValueWithinTxn(Supplier<OrderedVersionedIndex<Integer, String>> factory) {
        var index = factory.get();
        index.put(5, "original");

        var txn = index.txn();
        txn.remove(5);
        assertThat(txn.get(5)).isEmpty();
        assertThat(txn.size()).isZero();

        txn.put(5, "reinstated");
        assertThat(txn.get(5)).hasValue("reinstated");
        assertThat(txn.size()).isEqualTo(1);
        assertAllFullScanApisMatch(txn, List.of(Map.entry(5, "reinstated")));
        txn.commit();

        assertThat(index.get(5)).hasValue("reinstated");
    }

    // -------------------------------------------------------------------------
    // Size tracking
    // -------------------------------------------------------------------------

    @ParameterizedTest
    @MethodSource("indexFactories")
    void txnSizeTracksAllMutationTypesCorrectly(Supplier<OrderedVersionedIndex<Integer, String>> factory) {
        var index = factory.get();
        index.put(1, "a");
        index.put(2, "b");

        var txn = index.txn();
        assertThat(txn.size()).isEqualTo(2);

        txn.put(3, "c");
        assertThat(txn.size()).isEqualTo(3);

        txn.put(1, "updated"); // overwrite, no size change
        assertThat(txn.size()).isEqualTo(3);

        txn.remove(2);
        assertThat(txn.size()).isEqualTo(2);

        txn.remove(99); // absent key, no size change
        assertThat(txn.size()).isEqualTo(2);

        txn.put(4, "d");
        txn.put(5, "e");
        assertThat(txn.size()).isEqualTo(4);

        txn.commit();
        assertThat(index.size()).isEqualTo(4);
    }

    // -------------------------------------------------------------------------
    // forEach and iterator agree on both directions within txn
    // -------------------------------------------------------------------------

    @ParameterizedTest
    @MethodSource("indexFactories")
    void txnForEachAndIteratorAgreeOnBothDirections(Supplier<OrderedVersionedIndex<Integer, String>> factory) {
        var index = factory.get();
        var txn = index.txn();
        int[] insertOrder = {5, 1, 9, 3, 7, 2, 8, 4, 6};
        for (int k : insertOrder) txn.put(k, "v" + k);

        var feAsc = collectForEach(txn, Direction.ASC);
        var itAsc = collectIter(txn, Direction.ASC);
        assertThat(feAsc).isEqualTo(itAsc);

        var feDesc = collectForEach(txn, Direction.DESC);
        var itDesc = collectIter(txn, Direction.DESC);
        assertThat(feDesc).isEqualTo(itDesc);

        var reversed = new ArrayList<>(feAsc);
        Collections.reverse(reversed);
        assertThat(feDesc).isEqualTo(reversed);

        txn.commit();
    }

    // -------------------------------------------------------------------------
    // Range queries within txn — all four range types
    // -------------------------------------------------------------------------

    @ParameterizedTest
    @MethodSource("indexFactories")
    void txnAllFourRangeTypesViaAllApisReflectTxnPuts(Supplier<OrderedVersionedIndex<Integer, String>> factory) {
        var index = factory.get();
        var txn = index.txn();
        for (int k = 1; k <= 9; k++) txn.put(k, "v" + k);

        assertAllRangeApisMatch(txn, Range.closed(3, 6), List.of(
                Map.entry(3, "v3"), Map.entry(4, "v4"),
                Map.entry(5, "v5"), Map.entry(6, "v6")
        ));
        assertAllRangeApisMatch(txn, Range.open(2, 7), List.of(
                Map.entry(3, "v3"), Map.entry(4, "v4"),
                Map.entry(5, "v5"), Map.entry(6, "v6")
        ));
        assertAllRangeApisMatch(txn, Range.closedOpen(2, 5), List.of(
                Map.entry(2, "v2"), Map.entry(3, "v3"), Map.entry(4, "v4")
        ));
        assertAllRangeApisMatch(txn, Range.openClosed(4, 8), List.of(
                Map.entry(5, "v5"), Map.entry(6, "v6"),
                Map.entry(7, "v7"), Map.entry(8, "v8")
        ));
        txn.commit();
    }

    @ParameterizedTest
    @MethodSource("indexFactories")
    void txnAllFourRangeTypesViaAllApisReflectTxnRemoves(Supplier<OrderedVersionedIndex<Integer, String>> factory) {
        var index = factory.get();
        for (int k = 1; k <= 9; k++) index.put(k, "v" + k);

        var txn = index.txn();
        txn.remove(3);
        txn.remove(6);

        assertAllRangeApisMatch(txn, Range.closed(2, 7), List.of(
                Map.entry(2, "v2"), Map.entry(4, "v4"),
                Map.entry(5, "v5"), Map.entry(7, "v7")
        ));
        assertAllRangeApisMatch(txn, Range.open(3, 7), List.of(
                Map.entry(4, "v4"), Map.entry(5, "v5")
        ));
        assertAllRangeApisMatch(txn, Range.closedOpen(3, 7), List.of(
                Map.entry(4, "v4"), Map.entry(5, "v5")
        ));
        assertAllRangeApisMatch(txn, Range.openClosed(2, 6), List.of(
                Map.entry(4, "v4"), Map.entry(5, "v5")
        ));
        txn.commit();
    }

    @ParameterizedTest
    @MethodSource("indexFactories")
    void txnRangeQueryBelowAllKeysIsEmpty(Supplier<OrderedVersionedIndex<Integer, String>> factory) {
        var index = factory.get();
        var txn = index.txn();
        for (int k = 50; k <= 60; k++) txn.put(k, "v" + k);

        assertAllRangeApisMatch(txn, Range.closed(1, 10), List.of());
        assertAllRangeApisMatch(txn, Range.open(1, 10), List.of());
        txn.commit();
    }

    // -------------------------------------------------------------------------
    // Cascading splits — many puts in single txn
    // -------------------------------------------------------------------------

    @ParameterizedTest
    @MethodSource("indexFactories")
    void txnManySplitsPutsReadCorrectViaAllApisWithinTxn(Supplier<OrderedVersionedIndex<Integer, String>> factory) {
        var index = factory.get();
        var txn = index.txn();
        int count = 50;
        var expected = new TreeMap<Integer, String>();
        for (int k = 1; k <= count; k++) {
            txn.put(k, "v" + k);
            expected.put(k, "v" + k);
        }

        assertAllFullScanApisMatch(txn, sorted(expected));
        assertThat(txn.get(1)).hasValue("v1");
        assertThat(txn.get(25)).hasValue("v25");
        assertThat(txn.get(50)).hasValue("v50");
        assertThat(txn.contains(51)).isFalse();

        assertAllRangeApisMatch(txn, Range.closed(10, 20), List.of(
                Map.entry(10, "v10"), Map.entry(11, "v11"), Map.entry(12, "v12"),
                Map.entry(13, "v13"), Map.entry(14, "v14"), Map.entry(15, "v15"),
                Map.entry(16, "v16"), Map.entry(17, "v17"), Map.entry(18, "v18"),
                Map.entry(19, "v19"), Map.entry(20, "v20")
        ));
        txn.commit();

        assertThat(index.size()).isEqualTo(count);
    }

    @ParameterizedTest
    @MethodSource("indexFactories")
    void txnManySplitsDescendingPutsReadCorrectViaAllApisWithinTxn(Supplier<OrderedVersionedIndex<Integer, String>> factory) {
        var index = factory.get();
        var txn = index.txn();
        int count = 50;
        var expected = new TreeMap<Integer, String>();
        for (int k = count; k >= 1; k--) { // descending insert triggers left-side splits
            txn.put(k, "v" + k);
            expected.put(k, "v" + k);
        }

        assertAllFullScanApisMatch(txn, sorted(expected));
        txn.commit();
        assertThat(index.size()).isEqualTo(count);
    }

    // -------------------------------------------------------------------------
    // Cascading merges — many removes in single txn
    // -------------------------------------------------------------------------

    @ParameterizedTest
    @MethodSource("indexFactories")
    void txnManyMergesRemovesReadCorrectViaAllApisWithinTxn(Supplier<OrderedVersionedIndex<Integer, String>> factory) {
        var index = factory.get();
        int preload = 50;
        for (int k = 1; k <= preload; k++) index.put(k, "v" + k);

        var txn = index.txn();
        for (int k = 1; k <= 40; k++) txn.remove(k);

        var expected = new TreeMap<Integer, String>();
        for (int k = 41; k <= preload; k++) expected.put(k, "v" + k);

        assertAllFullScanApisMatch(txn, sorted(expected));
        assertThat(txn.get(1)).isEmpty();
        assertThat(txn.get(40)).isEmpty();
        assertThat(txn.get(41)).hasValue("v41");
        assertThat(txn.size()).isEqualTo(10);

        txn.commit();
        assertThat(index.size()).isEqualTo(10);
    }

    @ParameterizedTest
    @MethodSource("indexFactories")
    void txnAlternatingRemovesFromBothEndsReadCorrectWithinTxn(Supplier<OrderedVersionedIndex<Integer, String>> factory) {
        var index = factory.get();
        int preload = 30;
        for (int k = 1; k <= preload; k++) index.put(k, "v" + k);

        var txn = index.txn();
        // Remove alternating: from front and back simultaneously to trigger diverse merges
        for (int i = 0; i < 10; i++) {
            txn.remove(i + 1);          // removes 1..10
            txn.remove(preload - i);     // removes 30..21
        }

        // Remaining: 11..20
        var expected = new TreeMap<Integer, String>();
        for (int k = 11; k <= 20; k++) expected.put(k, "v" + k);
        assertAllFullScanApisMatch(txn, sorted(expected));
        assertThat(txn.size()).isEqualTo(10);

        txn.commit();
        assertThat(index.size()).isEqualTo(10);
    }

    // -------------------------------------------------------------------------
    // Interleaved puts and removes with step-by-step verification
    // -------------------------------------------------------------------------

    @ParameterizedTest
    @MethodSource("indexFactories")
    void txnInterleavedPutsAndRemovesVerifiedAtEachStep(Supplier<OrderedVersionedIndex<Integer, String>> factory) {
        var index = factory.get();
        for (int k : new int[]{1, 2, 3, 4, 5}) index.put(k, "pre" + k);

        var txn = index.txn();
        var expected = new TreeMap<Integer, String>();
        for (int k : new int[]{1, 2, 3, 4, 5}) expected.put(k, "pre" + k);

        // step 1: add new keys
        txn.put(6, "v6");
        txn.put(7, "v7");
        expected.put(6, "v6");
        expected.put(7, "v7");
        assertThat(txn.size()).isEqualTo(7);

        // step 2: overwrite existing
        txn.put(3, "updated3");
        expected.put(3, "updated3");
        assertThat(txn.get(3)).hasValue("updated3");

        // step 3: remove two keys
        txn.remove(1);
        txn.remove(4);
        expected.remove(1);
        expected.remove(4);
        assertThat(txn.size()).isEqualTo(5);
        assertThat(txn.get(1)).isEmpty();

        // step 4: reinsert previously removed key with new value
        txn.put(1, "new1");
        expected.put(1, "new1");
        assertThat(txn.get(1)).hasValue("new1");
        assertThat(txn.size()).isEqualTo(6);

        // step 5: put+remove same key within txn (net zero)
        txn.put(8, "v8");
        txn.remove(8);
        assertThat(txn.get(8)).isEmpty();
        assertThat(txn.size()).isEqualTo(6);

        assertAllFullScanApisMatch(txn, sorted(expected));
        txn.commit();

        for (var e : expected.entrySet()) {
            assertThat(index.get(e.getKey())).hasValue(e.getValue());
        }
        assertThat(index.get(4)).isEmpty();
        assertThat(index.get(8)).isEmpty();
    }

    // -------------------------------------------------------------------------
    // Isolation: mutations invisible before commit, all visible after
    // -------------------------------------------------------------------------

    @ParameterizedTest
    @MethodSource("indexFactories")
    void txnMutationsNotVisibleToIndexBeforeCommit(Supplier<OrderedVersionedIndex<Integer, String>> factory) {
        var index = factory.get();
        index.put(1, "existing");

        var txn = index.txn();
        txn.put(2, "txn");
        txn.put(3, "txn");
        txn.remove(1);

        // index still shows pre-txn committed state
        assertThat(index.get(1)).hasValue("existing");
        assertThat(index.get(2)).isEmpty();
        assertThat(index.get(3)).isEmpty();
        assertThat(index.size()).isEqualTo(1);

        // txn sees its own mutations
        assertThat(txn.get(1)).isEmpty();
        assertThat(txn.get(2)).hasValue("txn");
        assertThat(txn.size()).isEqualTo(2);

        txn.commit();

        assertThat(index.get(1)).isEmpty();
        assertThat(index.get(2)).hasValue("txn");
        assertThat(index.get(3)).hasValue("txn");
        assertThat(index.size()).isEqualTo(2);
    }

    @ParameterizedTest
    @MethodSource("indexFactories")
    void txnLargeMultiOpCommitMakesAllChangesAtomicallyVisible(Supplier<OrderedVersionedIndex<Integer, String>> factory) {
        var index = factory.get();
        for (int k = 1; k <= 20; k++) index.put(k, "old" + k);

        var snap = index.snapshot();

        var txn = index.txn();
        // overwrite first half, remove second half, add new keys
        for (int k = 1; k <= 10; k++) txn.put(k, "new" + k);
        for (int k = 11; k <= 20; k++) txn.remove(k);
        for (int k = 21; k <= 30; k++) txn.put(k, "extra" + k);

        // snapshot (pre-txn) still shows old state
        for (int k = 1; k <= 20; k++) assertThat(snap.get(k)).hasValue("old" + k);
        assertThat(snap.size()).isEqualTo(20);

        // index still shows old state before commit
        assertThat(index.size()).isEqualTo(20);

        txn.commit();

        // now all mutations visible
        assertThat(index.size()).isEqualTo(20); // 10 updated + 10 new = 20
        for (int k = 1; k <= 10; k++) assertThat(index.get(k)).hasValue("new" + k);
        for (int k = 11; k <= 20; k++) assertThat(index.get(k)).isEmpty();
        for (int k = 21; k <= 30; k++) assertThat(index.get(k)).hasValue("extra" + k);
    }

    // -------------------------------------------------------------------------
    // Ascending + descending insert pattern triggers diverse split paths
    // -------------------------------------------------------------------------

    @ParameterizedTest
    @MethodSource("indexFactories")
    void txnLargeInterleavedInsertRemoveCycle(Supplier<OrderedVersionedIndex<Integer, String>> factory) {
        var index = factory.get();

        // txn1: ascending inserts force right-side splits
        var txn1 = index.txn();
        for (int k = 1; k <= 30; k++) txn1.put(k, "asc" + k);
        txn1.commit();

        // txn2: descending removes trigger merges from the right side
        var txn2 = index.txn();
        for (int k = 30; k >= 1; k -= 2) txn2.remove(k); // removes 30, 28, ..., 2 (even)
        txn2.commit();

        // remaining: odd keys 1, 3, 5, ..., 29
        var expected = new ArrayList<Map.Entry<Integer, String>>();
        for (int k = 1; k <= 29; k += 2) expected.add(Map.entry(k, "asc" + k));
        assertThat(collectForEach(index, Direction.ASC)).isEqualTo(expected);
        assertThat(index.size()).isEqualTo(15);
    }
}

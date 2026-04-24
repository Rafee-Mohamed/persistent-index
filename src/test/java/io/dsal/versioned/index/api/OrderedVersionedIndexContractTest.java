package io.dsal.versioned.index.api;

import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Portable contract test for {@link OrderedVersionedIndex}.
 *
 * <p>Subclasses supply concrete index instances via {@link #indices()}.
 * Every test runs once per instance returned, covering different internal configurations.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class OrderedVersionedIndexContractTest {

    protected abstract Stream<OrderedVersionedIndex<Integer, String>> indices();

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    private static List<Map.Entry<Integer, String>> collect(
            OrderedVersionedIndex<Integer, String> index, Direction direction) {
        var out = new ArrayList<Map.Entry<Integer, String>>();
        index.forEach(direction, (k, v) -> out.add(Map.entry(k, v)));
        return out;
    }

    private static List<Map.Entry<Integer, String>> collectRange(
            OrderedVersionedIndex<Integer, String> index, Direction direction, Range<Integer> range) {
        var out = new ArrayList<Map.Entry<Integer, String>>();
        index.forEach(direction, range, (k, v) -> out.add(Map.entry(k, v)));
        return out;
    }

    private static List<Map.Entry<Integer, String>> collectSnapshot(
            Snapshot<Integer, String> snap, Direction direction) {
        var out = new ArrayList<Map.Entry<Integer, String>>();
        snap.forEach(direction, (k, v) -> out.add(Map.entry(k, v)));
        return out;
    }

    private static List<Map.Entry<Integer, String>> collectSnapshotRange(
            Snapshot<Integer, String> snap, Direction direction, Range<Integer> range) {
        var out = new ArrayList<Map.Entry<Integer, String>>();
        snap.forEach(direction, range, (k, v) -> out.add(Map.entry(k, v)));
        return out;
    }

    // ---------------------------------------------------------------------------
    // Empty state
    // ---------------------------------------------------------------------------

    @ParameterizedTest
    @MethodSource("indices")
    void emptyIndexHasSizeZero(OrderedVersionedIndex<Integer, String> index) {
        assertThat(index.size()).isZero();
    }

    @ParameterizedTest
    @MethodSource("indices")
    void emptyIndexGetReturnsEmpty(OrderedVersionedIndex<Integer, String> index) {
        assertThat(index.get(1)).isEmpty();
    }

    @ParameterizedTest
    @MethodSource("indices")
    void emptyIndexContainsReturnsFalse(OrderedVersionedIndex<Integer, String> index) {
        assertThat(index.contains(1)).isFalse();
    }

    @ParameterizedTest
    @MethodSource("indices")
    void emptyIndexRemoveReturnsEmpty(OrderedVersionedIndex<Integer, String> index) {
        assertThat(index.remove(1)).isEmpty();
    }

    @ParameterizedTest
    @MethodSource("indices")
    void emptyIndexIteratorHasNoElements(OrderedVersionedIndex<Integer, String> index) {
        assertThat(collect(index, Direction.ASC)).isEmpty();
        assertThat(collect(index, Direction.DESC)).isEmpty();
    }

    @ParameterizedTest
    @MethodSource("indices")
    void emptyIndexSnapshotHasSizeZero(OrderedVersionedIndex<Integer, String> index) {
        assertThat(index.snapshot().size()).isZero();
    }

    // ---------------------------------------------------------------------------
    // Basic CRUD
    // ---------------------------------------------------------------------------

    @ParameterizedTest
    @MethodSource("indices")
    void putNewKeyReturnsEmpty(OrderedVersionedIndex<Integer, String> index) {
        assertThat(index.put(1, "a")).isEmpty();
    }

    @ParameterizedTest
    @MethodSource("indices")
    void putAndGetRoundtrip(OrderedVersionedIndex<Integer, String> index) {
        index.put(1, "a");
        assertThat(index.get(1)).hasValue("a");
        assertThat(index.contains(1)).isTrue();
    }

    @ParameterizedTest
    @MethodSource("indices")
    void putReturnsPreviousValue(OrderedVersionedIndex<Integer, String> index) {
        index.put(1, "a");
        assertThat(index.put(1, "b")).hasValue("a");
        assertThat(index.get(1)).hasValue("b");
    }

    @ParameterizedTest
    @MethodSource("indices")
    void removeReturnsPreviousValue(OrderedVersionedIndex<Integer, String> index) {
        index.put(1, "a");
        assertThat(index.remove(1)).hasValue("a");
    }

    @ParameterizedTest
    @MethodSource("indices")
    void removeAbsentKeyReturnsEmpty(OrderedVersionedIndex<Integer, String> index) {
        index.put(1, "a");
        assertThat(index.remove(99)).isEmpty();
    }

    @ParameterizedTest
    @MethodSource("indices")
    void removeDeletesEntry(OrderedVersionedIndex<Integer, String> index) {
        index.put(1, "a");
        index.remove(1);
        assertThat(index.get(1)).isEmpty();
        assertThat(index.contains(1)).isFalse();
    }

    @ParameterizedTest
    @MethodSource("indices")
    void sizeTracksInsertionsAndRemovals(OrderedVersionedIndex<Integer, String> index) {
        assertThat(index.size()).isZero();
        index.put(1, "a");
        assertThat(index.size()).isEqualTo(1);
        index.put(2, "b");
        assertThat(index.size()).isEqualTo(2);
        index.put(1, "updated");
        assertThat(index.size()).isEqualTo(2);
        index.remove(1);
        assertThat(index.size()).isEqualTo(1);
        index.remove(2);
        assertThat(index.size()).isZero();
    }

    @ParameterizedTest
    @MethodSource("indices")
    void removeLastKeyEmptiesIndex(OrderedVersionedIndex<Integer, String> index) {
        index.put(1, "a");
        index.remove(1);
        assertThat(index.size()).isZero();
        assertThat(collect(index, Direction.ASC)).isEmpty();
    }

    // ---------------------------------------------------------------------------
    // Multi-key ordering and iteration
    // ---------------------------------------------------------------------------

    @ParameterizedTest
    @MethodSource("indices")
    void multipleKeysAreOrderedInAscIteration(OrderedVersionedIndex<Integer, String> index) {
        index.put(30, "c");
        index.put(10, "a");
        index.put(20, "b");

        assertThat(collect(index, Direction.ASC)).containsExactly(
                Map.entry(10, "a"), Map.entry(20, "b"), Map.entry(30, "c"));
    }

    @ParameterizedTest
    @MethodSource("indices")
    void descIterationIsReverseOfAsc(OrderedVersionedIndex<Integer, String> index) {
        for (int i = 1; i <= 10; i++) index.put(i, String.valueOf(i));

        var asc  = collect(index, Direction.ASC);
        var desc = collect(index, Direction.DESC);
        assertThat(desc).isEqualTo(asc.reversed());
    }

    @ParameterizedTest
    @MethodSource("indices")
    void forEachMatchesIteratorForBothDirections(OrderedVersionedIndex<Integer, String> index) {
        for (int i = 1; i <= 8; i++) index.put(i * 10, String.valueOf(i));

        var fromForEachAsc = collect(index, Direction.ASC);
        var fromIteratorAsc = new ArrayList<Map.Entry<Integer, String>>();
        var it = index.iterator(Direction.ASC, Map::entry);
        while (it.hasNext()) fromIteratorAsc.add(it.next());
        assertThat(fromForEachAsc).isEqualTo(fromIteratorAsc);

        var fromForEachDesc = collect(index, Direction.DESC);
        var fromIteratorDesc = new ArrayList<Map.Entry<Integer, String>>();
        var it2 = index.iterator(Direction.DESC, Map::entry);
        while (it2.hasNext()) fromIteratorDesc.add(it2.next());
        assertThat(fromForEachDesc).isEqualTo(fromIteratorDesc);
    }

    @ParameterizedTest
    @MethodSource("indices")
    void insertRemoveReinsertPreservesOrder(OrderedVersionedIndex<Integer, String> index) {
        for (int i = 1; i <= 10; i++) index.put(i, "v" + i);
        for (int i = 1; i <= 5; i++)  index.remove(i);
        for (int i = 1; i <= 5; i++)  index.put(i, "new" + i);

        var entries = collect(index, Direction.ASC);
        assertThat(entries).hasSize(10);
        for (int i = 0; i < entries.size() - 1; i++) {
            assertThat(entries.get(i).getKey()).isLessThan(entries.get(i + 1).getKey());
        }
    }

    // ---------------------------------------------------------------------------
    // Range queries — all four Range types and both directions
    // ---------------------------------------------------------------------------

    @ParameterizedTest
    @MethodSource("indices")
    void closedRangeIncludesBothBounds(OrderedVersionedIndex<Integer, String> index) {
        for (int i = 1; i <= 10; i++) index.put(i, "v" + i);

        assertThat(collectRange(index, Direction.ASC, Range.closed(3, 7))).containsExactly(
                Map.entry(3, "v3"), Map.entry(4, "v4"), Map.entry(5, "v5"),
                Map.entry(6, "v6"), Map.entry(7, "v7"));
    }

    @ParameterizedTest
    @MethodSource("indices")
    void openRangeExcludesBothBounds(OrderedVersionedIndex<Integer, String> index) {
        for (int i = 1; i <= 10; i++) index.put(i, "v" + i);

        assertThat(collectRange(index, Direction.ASC, Range.open(3, 7))).containsExactly(
                Map.entry(4, "v4"), Map.entry(5, "v5"), Map.entry(6, "v6"));
    }

    @ParameterizedTest
    @MethodSource("indices")
    void closedOpenRangeIncludesLowerExcludesUpper(OrderedVersionedIndex<Integer, String> index) {
        for (int i = 1; i <= 10; i++) index.put(i, "v" + i);

        assertThat(collectRange(index, Direction.ASC, Range.closedOpen(3, 7))).containsExactly(
                Map.entry(3, "v3"), Map.entry(4, "v4"), Map.entry(5, "v5"),
                Map.entry(6, "v6"));
    }

    @ParameterizedTest
    @MethodSource("indices")
    void openClosedRangeExcludesLowerIncludesUpper(OrderedVersionedIndex<Integer, String> index) {
        for (int i = 1; i <= 10; i++) index.put(i, "v" + i);

        assertThat(collectRange(index, Direction.ASC, Range.openClosed(3, 7))).containsExactly(
                Map.entry(4, "v4"), Map.entry(5, "v5"), Map.entry(6, "v6"),
                Map.entry(7, "v7"));
    }

    @ParameterizedTest
    @MethodSource("indices")
    void invertedClosedRangeIsEmpty(OrderedVersionedIndex<Integer, String> index) {
        for (int i = 1; i <= 10; i++) index.put(i, "v" + i);

        assertThat(collectRange(index, Direction.ASC, Range.closed(7, 3))).isEmpty();
    }

    @ParameterizedTest
    @MethodSource("indices")
    void openRangeWithEqualBoundsIsEmpty(OrderedVersionedIndex<Integer, String> index) {
        for (int i = 1; i <= 5; i++) index.put(i, "v" + i);

        assertThat(collectRange(index, Direction.ASC, Range.open(3, 3))).isEmpty();
        assertThat(collectRange(index, Direction.ASC, Range.closedOpen(3, 3))).isEmpty();
        assertThat(collectRange(index, Direction.ASC, Range.openClosed(3, 3))).isEmpty();
    }

    @ParameterizedTest
    @MethodSource("indices")
    void closedRangeWithEqualBoundsReturnsSingleEntry(OrderedVersionedIndex<Integer, String> index) {
        for (int i = 1; i <= 5; i++) index.put(i, "v" + i);

        assertThat(collectRange(index, Direction.ASC, Range.closed(3, 3)))
                .containsExactly(Map.entry(3, "v3"));
    }

    @ParameterizedTest
    @MethodSource("indices")
    void rangeOutsideAllKeysIsEmpty(OrderedVersionedIndex<Integer, String> index) {
        for (int i = 1; i <= 5; i++) index.put(i, "v" + i);

        assertThat(collectRange(index, Direction.ASC, Range.closed(10, 20))).isEmpty();
        assertThat(collectRange(index, Direction.ASC, Range.closed(-5, 0))).isEmpty();
    }

    @ParameterizedTest
    @MethodSource("indices")
    void closedRangeInDescOrderIsReverseOfAsc(OrderedVersionedIndex<Integer, String> index) {
        for (int i = 1; i <= 10; i++) index.put(i, "v" + i);

        var asc  = collectRange(index, Direction.ASC,  Range.closed(3, 7));
        var desc = collectRange(index, Direction.DESC, Range.closed(3, 7));
        assertThat(desc).isEqualTo(asc.reversed());
    }

    @ParameterizedTest
    @MethodSource("indices")
    void fullClosedRangeMatchesFullIteration(OrderedVersionedIndex<Integer, String> index) {
        for (int i = 1; i <= 15; i++) index.put(i, "v" + i);

        assertThat(collectRange(index, Direction.ASC, Range.closed(1, 15)))
                .isEqualTo(collect(index, Direction.ASC));
    }

    @ParameterizedTest
    @MethodSource("indices")
    void rangeForEachMatchesRangeIterator(OrderedVersionedIndex<Integer, String> index) {
        for (int i = 1; i <= 10; i++) index.put(i, "v" + i);
        var range = Range.closed(2, 8);

        var fromForEach = collectRange(index, Direction.ASC, range);
        var fromIterator = new ArrayList<Map.Entry<Integer, String>>();
        var it = index.iterator(Direction.ASC, range, Map::entry);
        while (it.hasNext()) fromIterator.add(it.next());
        assertThat(fromForEach).isEqualTo(fromIterator);
    }

    // ---------------------------------------------------------------------------
    // Snapshot isolation
    // ---------------------------------------------------------------------------

    @ParameterizedTest
    @MethodSource("indices")
    void snapshotBeforeCommitDoesNotSeeLaterPuts(OrderedVersionedIndex<Integer, String> index) {
        var snap = index.snapshot();
        index.put(1, "a");
        assertThat(snap.get(1)).isEmpty();
        assertThat(snap.contains(1)).isFalse();
        assertThat(snap.size()).isZero();
    }

    @ParameterizedTest
    @MethodSource("indices")
    void snapshotAfterCommitSeesNewState(OrderedVersionedIndex<Integer, String> index) {
        index.put(1, "a");
        var snap = index.snapshot();
        assertThat(snap.get(1)).hasValue("a");
        assertThat(snap.contains(1)).isTrue();
        assertThat(snap.size()).isEqualTo(1);
    }

    @ParameterizedTest
    @MethodSource("indices")
    void snapshotIsStableAcrossSubsequentMutations(OrderedVersionedIndex<Integer, String> index) {
        index.put(1, "a");
        var snap = index.snapshot();
        index.put(2, "b");
        index.put(3, "c");
        index.remove(1);

        assertThat(snap.size()).isEqualTo(1);
        assertThat(snap.get(1)).hasValue("a");
        assertThat(snap.get(2)).isEmpty();
        assertThat(snap.get(3)).isEmpty();
    }

    @ParameterizedTest
    @MethodSource("indices")
    void twoSnapshotsAtDifferentVersionsAreIndependent(OrderedVersionedIndex<Integer, String> index) {
        var snap1 = index.snapshot();
        index.put(1, "a");
        var snap2 = index.snapshot();
        index.put(2, "b");
        var snap3 = index.snapshot();

        assertThat(snap1.size()).isZero();
        assertThat(snap2.size()).isEqualTo(1);
        assertThat(snap3.size()).isEqualTo(2);
    }

    @ParameterizedTest
    @MethodSource("indices")
    void snapshotIteratorSeesOnlyItsVersion(OrderedVersionedIndex<Integer, String> index) {
        index.put(1, "a");
        index.put(2, "b");
        var snap = index.snapshot();
        index.put(3, "c");
        index.put(4, "d");

        assertThat(collectSnapshot(snap, Direction.ASC))
                .containsExactly(Map.entry(1, "a"), Map.entry(2, "b"));
    }

    @ParameterizedTest
    @MethodSource("indices")
    void snapshotRangeQuerySeesOnlyItsVersion(OrderedVersionedIndex<Integer, String> index) {
        for (int i = 1; i <= 5; i++) index.put(i, "v" + i);
        var snap = index.snapshot();
        for (int i = 6; i <= 10; i++) index.put(i, "v" + i);

        var rangeEntries = collectSnapshotRange(snap, Direction.ASC, Range.closed(1, 10));
        assertThat(rangeEntries).hasSize(5);
        assertThat(rangeEntries.getLast().getKey()).isEqualTo(5);
    }

    // ---------------------------------------------------------------------------
    // Txn — read-your-writes and snapshot base state
    // ---------------------------------------------------------------------------

    @ParameterizedTest
    @MethodSource("indices")
    void txnGetSeesOwnPuts(OrderedVersionedIndex<Integer, String> index) {
        var txn = index.txn();
        txn.put(1, "a");
        assertThat(txn.get(1)).hasValue("a");
        assertThat(txn.contains(1)).isTrue();
        txn.commit();
    }

    @ParameterizedTest
    @MethodSource("indices")
    void txnGetSeesOwnRemoves(OrderedVersionedIndex<Integer, String> index) {
        index.put(1, "a");
        var txn = index.txn();
        txn.remove(1);
        assertThat(txn.get(1)).isEmpty();
        assertThat(txn.contains(1)).isFalse();
        txn.commit();
    }

    @ParameterizedTest
    @MethodSource("indices")
    void txnSnapshotDoesNotSeeTxnLocalMutations(OrderedVersionedIndex<Integer, String> index) {
        index.put(1, "a");
        var txn = index.txn();
        txn.put(2, "b");
        txn.remove(1);

        var base = txn.snapshot();
        assertThat(base.get(1)).hasValue("a");
        assertThat(base.get(2)).isEmpty();
        txn.commit();
    }

    @ParameterizedTest
    @MethodSource("indices")
    void txnSnapshotEqualsIndexSnapshotAtTxnStart(OrderedVersionedIndex<Integer, String> index) {
        index.put(1, "a");
        index.put(2, "b");
        var snapBefore = index.snapshot();
        var txn = index.txn();
        var txnBase = txn.snapshot();

        assertThat(collectSnapshot(txnBase, Direction.ASC))
                .isEqualTo(collectSnapshot(snapBefore, Direction.ASC));
        txn.commit();
    }

    @ParameterizedTest
    @MethodSource("indices")
    void txnMutationsNotVisibleToIndexBeforeCommit(OrderedVersionedIndex<Integer, String> index) {
        var txn = index.txn();
        txn.put(1, "a");
        txn.put(2, "b");

        assertThat(index.get(1)).isEmpty();
        assertThat(index.get(2)).isEmpty();
        txn.commit();
    }

    @ParameterizedTest
    @MethodSource("indices")
    void txnMutationsVisibleToIndexAfterCommit(OrderedVersionedIndex<Integer, String> index) {
        var txn = index.txn();
        txn.put(1, "a");
        txn.put(2, "b");
        txn.remove(1);
        txn.commit();

        assertThat(index.get(1)).isEmpty();
        assertThat(index.get(2)).hasValue("b");
    }

    @ParameterizedTest
    @MethodSource("indices")
    void txnMultipleOpsCommitAtomically(OrderedVersionedIndex<Integer, String> index) {
        for (int i = 1; i <= 5; i++) index.put(i, "old" + i);

        var snap = index.snapshot();
        var txn = index.txn();
        for (int i = 1; i <= 5; i++) txn.put(i, "new" + i);

        assertThat(collectSnapshot(snap, Direction.ASC))
                .allMatch(e -> e.getValue().startsWith("old"));
        txn.commit();
        for (int i = 1; i <= 5; i++) assertThat(index.get(i)).hasValue("new" + i);
    }

    // ---------------------------------------------------------------------------
    // Txn lifecycle
    // ---------------------------------------------------------------------------

    @ParameterizedTest
    @MethodSource("indices")
    void txnCommittedFlagUpdatesAfterCommit(OrderedVersionedIndex<Integer, String> index) {
        var txn = index.txn();
        assertThat(txn.committed()).isFalse();
        txn.commit();
        assertThat(txn.committed()).isTrue();
    }

    @ParameterizedTest
    @MethodSource("indices")
    void txnDoubleCommitThrowsIllegalState(OrderedVersionedIndex<Integer, String> index) {
        var txn = index.txn();
        txn.commit();
        assertThatThrownBy(txn::commit).isInstanceOf(IllegalStateException.class);
    }

    @ParameterizedTest
    @MethodSource("indices")
    void txnBlockAutoCommits(OrderedVersionedIndex<Integer, String> index) {
        var old = index.<Optional<String>, RuntimeException>txn(th -> th.put(1, "a"));
        assertThat(old).isEmpty();
        assertThat(index.get(1)).hasValue("a");
    }

    @ParameterizedTest
    @MethodSource("indices")
    void txnActionAutoCommits(OrderedVersionedIndex<Integer, String> index) {
        index.txn((TxnAction<Integer, String, RuntimeException>) th -> {
            th.put(1, "a");
            th.put(2, "b");
        });
        assertThat(index.get(1)).hasValue("a");
        assertThat(index.get(2)).hasValue("b");
    }

    @ParameterizedTest
    @MethodSource("indices")
    void txnBlockDoesNotCommitOnException(OrderedVersionedIndex<Integer, String> index) {
        try {
            index.txn((TxnAction<Integer, String, RuntimeException>) th -> {
                th.put(1, "a");
                throw new RuntimeException("boom");
            });
        } catch (RuntimeException ignored) {}

        assertThat(index.get(1)).isEmpty();
    }
}

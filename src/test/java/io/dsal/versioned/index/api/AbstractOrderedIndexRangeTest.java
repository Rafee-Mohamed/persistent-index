package io.dsal.versioned.index.api;

import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Abstract range-query suite for any {@link OrderedVersionedIndex} implementation.
 *
 * <p>Covers the full read surface of ranged access:
 * <ul>
 *   <li>All four {@link Range} types: {@code closed}, {@code open}, {@code closedOpen},
 *       {@code openClosed}.</li>
 *   <li>Both {@link Direction#ASC} and {@link Direction#DESC}.</li>
 *   <li>Both {@code forEach(Direction, Range, BiConsumer)} and
 *       {@code iterator(Direction, Range)}.</li>
 *   <li>Edge cases: empty ranges (inverted bounds, singleton open, below/above all keys),
 *       partial overlaps, and large sub-list equivalence.</li>
 * </ul>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class AbstractOrderedIndexRangeTest {

    protected abstract Stream<Supplier<OrderedVersionedIndex<Integer, String>>> indexFactories();

    // -------------------------------------------------------------------------
    // Core assertion helper — verifies all 4 API variants for one range
    // -------------------------------------------------------------------------

    /**
     * Asserts that {@code forEach(ASC)}, {@code forEach(DESC)}, {@code iterator(ASC)},
     * and {@code iterator(DESC)} all agree with {@code expectedAsc} (and its reverse).
     */
    private static void assertAllApis(
            OrderedVersionedIndex<Integer, String> index,
            Range<Integer> range,
            List<Map.Entry<Integer, String>> expectedAsc) {

        var expectedDesc = new ArrayList<>(expectedAsc);
        Collections.reverse(expectedDesc);

        var forEachAsc = new ArrayList<Map.Entry<Integer, String>>();
        index.forEach(Direction.ASC, range, (k, v) -> forEachAsc.add(Map.entry(k, v)));
        assertThat(forEachAsc).as("forEach(ASC, %s)", range).isEqualTo(expectedAsc);

        var forEachDesc = new ArrayList<Map.Entry<Integer, String>>();
        index.forEach(Direction.DESC, range, (k, v) -> forEachDesc.add(Map.entry(k, v)));
        assertThat(forEachDesc).as("forEach(DESC, %s)", range).isEqualTo(expectedDesc);

        var iterAsc = collectRange(index, Direction.ASC, range);
        assertThat(iterAsc).as("iterator(ASC, %s)", range).isEqualTo(expectedAsc);

        var iterDesc = collectRange(index, Direction.DESC, range);
        assertThat(iterDesc).as("iterator(DESC, %s)", range).isEqualTo(expectedDesc);
    }

    private static List<Map.Entry<Integer, String>> collectRange(
            OrderedVersionedIndex<Integer, String> index, Direction dir, Range<Integer> range) {
        var out = new ArrayList<Map.Entry<Integer, String>>();
        Iterator<? extends Entry<Integer, String>> it = index.iterator(dir, range);
        it.forEachRemaining(e -> out.add(Map.entry(e.key(), e.value())));
        return out;
    }

    private static List<Map.Entry<Integer, String>> fullScan(
            OrderedVersionedIndex<Integer, String> index, Direction direction) {
        var out = new ArrayList<Map.Entry<Integer, String>>();
        index.forEach(direction, (k, v) -> out.add(Map.entry(k, v)));
        return out;
    }

    // -------------------------------------------------------------------------
    // Closed range [lo, hi] — both bounds inclusive
    // -------------------------------------------------------------------------

    @ParameterizedTest
    @MethodSource("indexFactories")
    void closedRangeIncludesBothBoundsAscAndDesc(Supplier<OrderedVersionedIndex<Integer, String>> factory) {
        var index = factory.get();
        for (int k = 1; k <= 9; k++) index.put(k, "v" + k);

        assertAllApis(index, Range.closed(3, 6), List.of(
                Map.entry(3, "v3"), Map.entry(4, "v4"),
                Map.entry(5, "v5"), Map.entry(6, "v6")
        ));
    }

    @ParameterizedTest
    @MethodSource("indexFactories")
    void closedRangeSingletonKeyContainsExactlyThatKey(Supplier<OrderedVersionedIndex<Integer, String>> factory) {
        var index = factory.get();
        for (int k = 1; k <= 5; k++) index.put(k, "v" + k);

        assertAllApis(index, Range.closed(3, 3), List.of(Map.entry(3, "v3")));
    }

    @ParameterizedTest
    @MethodSource("indexFactories")
    void closedRangeInvertedBoundsIsEmpty(Supplier<OrderedVersionedIndex<Integer, String>> factory) {
        var index = factory.get();
        for (int k = 1; k <= 5; k++) index.put(k, "v" + k);

        assertAllApis(index, Range.closed(5, 1), List.of());
    }

    // -------------------------------------------------------------------------
    // Open range (lo, hi) — both bounds exclusive
    // -------------------------------------------------------------------------

    @ParameterizedTest
    @MethodSource("indexFactories")
    void openRangeExcludesBothBoundsAscAndDesc(Supplier<OrderedVersionedIndex<Integer, String>> factory) {
        var index = factory.get();
        for (int k = 1; k <= 5; k++) index.put(k, "v" + k);

        assertAllApis(index, Range.open(1, 5), List.of(
                Map.entry(2, "v2"), Map.entry(3, "v3"), Map.entry(4, "v4")
        ));
    }

    @ParameterizedTest
    @MethodSource("indexFactories")
    void openRangeSingletonBoundsIsEmpty(Supplier<OrderedVersionedIndex<Integer, String>> factory) {
        var index = factory.get();
        for (int k = 1; k <= 5; k++) index.put(k, "v" + k);

        assertAllApis(index, Range.open(3, 3), List.of());
    }

    @ParameterizedTest
    @MethodSource("indexFactories")
    void openRangeAdjacentBoundsIsEmpty(Supplier<OrderedVersionedIndex<Integer, String>> factory) {
        var index = factory.get();
        for (int k = 1; k <= 5; k++) index.put(k, "v" + k);

        assertAllApis(index, Range.open(2, 3), List.of());
    }

    @ParameterizedTest
    @MethodSource("indexFactories")
    void openRangeInvertedBoundsIsEmpty(Supplier<OrderedVersionedIndex<Integer, String>> factory) {
        var index = factory.get();
        for (int k = 1; k <= 5; k++) index.put(k, "v" + k);

        assertAllApis(index, Range.open(5, 1), List.of());
    }

    // -------------------------------------------------------------------------
    // Half-open [lo, hi) — lower inclusive, upper exclusive
    // -------------------------------------------------------------------------

    @ParameterizedTest
    @MethodSource("indexFactories")
    void closedOpenRangeIncludesLowerExcludesUpperAscAndDesc(Supplier<OrderedVersionedIndex<Integer, String>> factory) {
        var index = factory.get();
        for (int k = 1; k <= 5; k++) index.put(k, "v" + k);

        assertAllApis(index, Range.closedOpen(1, 4), List.of(
                Map.entry(1, "v1"), Map.entry(2, "v2"), Map.entry(3, "v3")
        ));
    }

    @ParameterizedTest
    @MethodSource("indexFactories")
    void closedOpenRangeSingletonBoundsIsEmpty(Supplier<OrderedVersionedIndex<Integer, String>> factory) {
        var index = factory.get();
        for (int k = 1; k <= 5; k++) index.put(k, "v" + k);

        assertAllApis(index, Range.closedOpen(3, 3), List.of());
    }

    // -------------------------------------------------------------------------
    // Half-open (lo, hi] — lower exclusive, upper inclusive
    // -------------------------------------------------------------------------

    @ParameterizedTest
    @MethodSource("indexFactories")
    void openClosedRangeExcludesLowerIncludesUpperAscAndDesc(Supplier<OrderedVersionedIndex<Integer, String>> factory) {
        var index = factory.get();
        for (int k = 1; k <= 5; k++) index.put(k, "v" + k);

        assertAllApis(index, Range.openClosed(1, 4), List.of(
                Map.entry(2, "v2"), Map.entry(3, "v3"), Map.entry(4, "v4")
        ));
    }

    @ParameterizedTest
    @MethodSource("indexFactories")
    void openClosedRangeSingletonBoundsIsEmpty(Supplier<OrderedVersionedIndex<Integer, String>> factory) {
        var index = factory.get();
        for (int k = 1; k <= 5; k++) index.put(k, "v" + k);

        assertAllApis(index, Range.openClosed(3, 3), List.of());
    }

    // -------------------------------------------------------------------------
    // Boundary conditions — all range types
    // -------------------------------------------------------------------------

    @ParameterizedTest
    @MethodSource("indexFactories")
    void allRangeTypesEmptyWhenBelowAllKeys(Supplier<OrderedVersionedIndex<Integer, String>> factory) {
        var index = factory.get();
        index.put(50, "a");
        index.put(60, "b");

        assertAllApis(index, Range.closed(0, 10), List.of());
        assertAllApis(index, Range.open(0, 10), List.of());
        assertAllApis(index, Range.closedOpen(0, 10), List.of());
        assertAllApis(index, Range.openClosed(0, 10), List.of());
    }

    @ParameterizedTest
    @MethodSource("indexFactories")
    void allRangeTypesEmptyWhenAboveAllKeys(Supplier<OrderedVersionedIndex<Integer, String>> factory) {
        var index = factory.get();
        index.put(1, "a");
        index.put(2, "b");

        assertAllApis(index, Range.closed(100, 200), List.of());
        assertAllApis(index, Range.open(100, 200), List.of());
        assertAllApis(index, Range.closedOpen(100, 200), List.of());
        assertAllApis(index, Range.openClosed(100, 200), List.of());
    }

    // -------------------------------------------------------------------------
    // Partial overlap — left and right edges
    // -------------------------------------------------------------------------

    @ParameterizedTest
    @MethodSource("indexFactories")
    void partialOverlapLeftEdgeClosedRange(Supplier<OrderedVersionedIndex<Integer, String>> factory) {
        var index = factory.get();
        for (int k : new int[]{5, 15, 25}) index.put(k, "p" + k);

        assertAllApis(index, Range.closed(0, 10), List.of(Map.entry(5, "p5")));
    }

    @ParameterizedTest
    @MethodSource("indexFactories")
    void partialOverlapRightEdgeClosedRange(Supplier<OrderedVersionedIndex<Integer, String>> factory) {
        var index = factory.get();
        for (int k : new int[]{5, 15, 25}) index.put(k, "p" + k);

        assertAllApis(index, Range.closed(20, 30), List.of(Map.entry(25, "p25")));
    }

    // -------------------------------------------------------------------------
    // DESC is exact reverse of ASC — verified for all four range types
    // -------------------------------------------------------------------------

    @ParameterizedTest
    @MethodSource("indexFactories")
    void descIsExactReverseOfAscForAllRangeTypes(Supplier<OrderedVersionedIndex<Integer, String>> factory) {
        var index = factory.get();
        for (int k = 1; k <= 9; k++) index.put(k, "v" + k);

        Range<Integer>[] ranges = new Range[]{
                Range.closed(2, 7),
                Range.open(2, 7),
                Range.closedOpen(2, 7),
                Range.openClosed(2, 7)
        };

        for (Range<Integer> range : ranges) {
            var asc = new ArrayList<Map.Entry<Integer, String>>();
            index.forEach(Direction.ASC, range, (k, v) -> asc.add(Map.entry(k, v)));

            var desc = new ArrayList<Map.Entry<Integer, String>>();
            index.forEach(Direction.DESC, range, (k, v) -> desc.add(Map.entry(k, v)));

            var reversed = new ArrayList<>(asc);
            Collections.reverse(reversed);
            assertThat(desc).as("DESC should be reverse of ASC for %s", range).isEqualTo(reversed);

            var iterAsc = collectRange(index, Direction.ASC, range);
            var iterDesc = collectRange(index, Direction.DESC, range);
            Collections.reverse(iterAsc);
            assertThat(iterDesc).as("iterator DESC should be reverse of iterator ASC for %s", range).isEqualTo(iterAsc);
        }
    }

    // -------------------------------------------------------------------------
    // Full-range equivalent to full scan
    // -------------------------------------------------------------------------

    @ParameterizedTest
    @MethodSource("indexFactories")
    void closedRangeSpanningAllKeysEqualsFullScan(Supplier<OrderedVersionedIndex<Integer, String>> factory) {
        var index = factory.get();
        int[] keys = {7, 2, 8, 0, 5, 3, 1, 6, 4};
        for (int k : keys) index.put(k, "v" + k);

        var fullAsc = fullScan(index, Direction.ASC);
        var fullDesc = fullScan(index, Direction.DESC);

        var range = Range.closed(0, 8);
        assertThat(collectRange(index, Direction.ASC, range)).isEqualTo(fullAsc);
        assertThat(collectRange(index, Direction.DESC, range)).isEqualTo(fullDesc);

        var forEachAsc = new ArrayList<Map.Entry<Integer, String>>();
        index.forEach(Direction.ASC, range, (k, v) -> forEachAsc.add(Map.entry(k, v)));
        assertThat(forEachAsc).isEqualTo(fullAsc);
    }

    // -------------------------------------------------------------------------
    // Large sub-list equivalence
    // -------------------------------------------------------------------------

    @ParameterizedTest
    @MethodSource("indexFactories")
    void closedRangeMatchesSortedReferenceSubList(Supplier<OrderedVersionedIndex<Integer, String>> factory) {
        final int keyCount = 100;
        var index = factory.get();
        var sortedRef = new ArrayList<Map.Entry<Integer, String>>(keyCount);
        for (int k = 0; k < keyCount; k++) {
            index.put(k, "v" + k);
            sortedRef.add(Map.entry(k, "v" + k));
        }

        final int from = 10, to = 50;
        var expectedAsc = new ArrayList<>(sortedRef.subList(from, to + 1));
        assertThat(expectedAsc).hasSize(to - from + 1);

        assertAllApis(index, Range.closed(from, to), expectedAsc);
    }
}

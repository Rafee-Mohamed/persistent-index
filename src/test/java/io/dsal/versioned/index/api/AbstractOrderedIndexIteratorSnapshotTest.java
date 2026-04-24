package io.dsal.versioned.index.api;

import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Abstract iterator snapshot-isolation suite for any {@link OrderedVersionedIndex} implementation.
 * Verifies that an iterator or range iterator captures the committed state at creation time
 * and is unaffected by subsequent mutations — for both directions and all range types.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class AbstractOrderedIndexIteratorSnapshotTest {

    protected abstract Stream<Supplier<OrderedVersionedIndex<Integer, String>>> indexFactories();

    private static List<Map.Entry<Integer, String>> collect(Iterator<? extends Entry<Integer, String>> it) {
        var out = new ArrayList<Map.Entry<Integer, String>>();
        it.forEachRemaining(e -> out.add(Map.entry(e.key(), e.value())));
        return out;
    }

    private static List<Map.Entry<Integer, String>> fullScan(
            OrderedVersionedIndex<Integer, String> index, Direction direction) {
        var out = new ArrayList<Map.Entry<Integer, String>>();
        index.forEach(direction, (k, v) -> out.add(Map.entry(k, v)));
        return out;
    }

    @ParameterizedTest
    @MethodSource("indexFactories")
    void fullAscIteratorIgnoresPutsAfterCreation(Supplier<OrderedVersionedIndex<Integer, String>> factory) {
        var index = factory.get();
        for (int k = 1; k <= 4; k++) index.put(k, "v" + k);

        var it = index.iterator(Direction.ASC);
        index.put(5, "v5");
        index.put(6, "v6");

        assertThat(collect(it)).containsExactly(
                Map.entry(1, "v1"), Map.entry(2, "v2"),
                Map.entry(3, "v3"), Map.entry(4, "v4")
        );
        assertThat(fullScan(index, Direction.ASC)).hasSize(6);
    }

    @ParameterizedTest
    @MethodSource("indexFactories")
    void fullDescIteratorIgnoresPutsAfterCreation(Supplier<OrderedVersionedIndex<Integer, String>> factory) {
        var index = factory.get();
        for (int k = 1; k <= 4; k++) index.put(k, "v" + k);

        var it = index.iterator(Direction.DESC);
        index.put(0, "v0");

        assertThat(collect(it)).containsExactly(
                Map.entry(4, "v4"), Map.entry(3, "v3"),
                Map.entry(2, "v2"), Map.entry(1, "v1")
        );
        assertThat(fullScan(index, Direction.ASC)).hasSize(5);
    }

    @ParameterizedTest
    @MethodSource("indexFactories")
    void fullAscIteratorIgnoresRemovesAfterCreation(Supplier<OrderedVersionedIndex<Integer, String>> factory) {
        var index = factory.get();
        index.put(1, "a");
        index.put(2, "b");
        index.put(3, "c");

        var it = index.iterator(Direction.ASC);
        index.remove(2);

        assertThat(collect(it)).containsExactly(
                Map.entry(1, "a"), Map.entry(2, "b"), Map.entry(3, "c")
        );
        assertThat(fullScan(index, Direction.ASC)).containsExactly(
                Map.entry(1, "a"), Map.entry(3, "c")
        );
    }

    @ParameterizedTest
    @MethodSource("indexFactories")
    void fullDescIteratorIgnoresRemovesAfterCreation(Supplier<OrderedVersionedIndex<Integer, String>> factory) {
        var index = factory.get();
        index.put(1, "a");
        index.put(2, "b");
        index.put(3, "c");

        var it = index.iterator(Direction.DESC);
        index.remove(2);

        assertThat(collect(it)).containsExactly(
                Map.entry(3, "c"), Map.entry(2, "b"), Map.entry(1, "a")
        );
    }

    @ParameterizedTest
    @MethodSource("indexFactories")
    void closedRangeAscIteratorSnapshotIgnoresMutations(Supplier<OrderedVersionedIndex<Integer, String>> factory) {
        var index = factory.get();
        int[] keys = {7, 2, 8, 0, 5, 3, 1, 6, 4};
        for (int k : keys) index.put(k, "v" + k);

        var snap = index.snapshot();
        var expectedRange = new ArrayList<Map.Entry<Integer, String>>();
        snap.forEach(Direction.ASC, Range.closed(3, 6), (k, v) -> expectedRange.add(Map.entry(k, v)));

        var it = index.iterator(Direction.ASC, Range.closed(3, 6));
        index.put(100, "extra");
        index.remove(4);

        assertThat(collect(it)).isEqualTo(expectedRange);
    }

    @ParameterizedTest
    @MethodSource("indexFactories")
    void closedRangeDescIteratorSnapshotIgnoresMutations(Supplier<OrderedVersionedIndex<Integer, String>> factory) {
        var index = factory.get();
        int[] keys = {7, 2, 8, 0, 5, 3, 1, 6, 4};
        for (int k : keys) index.put(k, "v" + k);

        var snap = index.snapshot();
        var expectedRange = new ArrayList<Map.Entry<Integer, String>>();
        snap.forEach(Direction.DESC, Range.closed(3, 6), (k, v) -> expectedRange.add(Map.entry(k, v)));

        var it = index.iterator(Direction.DESC, Range.closed(3, 6));
        index.put(100, "extra");
        index.remove(5);

        assertThat(collect(it)).isEqualTo(expectedRange);
    }

    @ParameterizedTest
    @MethodSource("indexFactories")
    void openRangeAscIteratorSnapshotIgnoresMutations(Supplier<OrderedVersionedIndex<Integer, String>> factory) {
        var index = factory.get();
        for (int k = 1; k <= 7; k++) index.put(k, "v" + k);

        var snap = index.snapshot();
        var expectedRange = new ArrayList<Map.Entry<Integer, String>>();
        snap.forEach(Direction.ASC, Range.open(2, 6), (k, v) -> expectedRange.add(Map.entry(k, v)));

        var it = index.iterator(Direction.ASC, Range.open(2, 6));
        index.put(3, "mutated");
        index.put(8, "extra");

        assertThat(collect(it)).isEqualTo(expectedRange);
    }
}

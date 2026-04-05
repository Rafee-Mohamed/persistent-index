package io.dsal.persistent.index.core;

import io.dsal.persistent.index.testsupport.TestKeyFixtures;
import io.dsal.persistent.index.testsupport.TreeStructureAssertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class PersistentBPlusTreeRangeTest {

    private static PersistentBPlusTree<Integer, String> newTree(int maxKeys) {
        return new PersistentBPlusTree<>(maxKeys, TestKeyFixtures.integerArrayKeyStorageFactory());
    }

    private static List<KeyVal<Integer, String>> collect(Iterator<KeyVal<Integer, String>> it) {
        var out = new ArrayList<KeyVal<Integer, String>>();
        it.forEachRemaining(out::add);
        return out;
    }

    private static List<KeyVal<Integer, String>> collectAll(PersistentBPlusTree<Integer, String> tree) {
        return collect(tree.iterator());
    }

    @ParameterizedTest
    @ValueSource(ints = {2, 3, 4, 5})
    void inclusiveRangeMatchesFullScanOrderAndBoundedIterator(int maxKeys) {
        var tree = newTree(maxKeys);
        int[] keys = {5, 2, 8, 1, 9};
        for (int k : keys) {
            tree.put(k, "v" + k);
        }
        var expected = IntStream.of(keys).sorted().mapToObj(k -> KeyVal.of(k, "v" + k)).toList();
        assertThat(collectAll(tree)).isEqualTo(expected);
        assertThat(tree.range(2, 8)).isEqualTo(List.of(
                KeyVal.of(2, "v2"),
                KeyVal.of(5, "v5"),
                KeyVal.of(8, "v8")
        ));
        assertThat(collect(tree.rangeIterator(2, 8))).isEqualTo(tree.range(2, 8));
    }

    @ParameterizedTest
    @ValueSource(ints = {2, 3, 4, 5})
    void rangeEmptyWhenStrictlyBelowAllKeys(int maxKeys) {
        var tree = newTree(maxKeys);
        tree.put(50, "a");
        tree.put(60, "b");
        assertThat(tree.range(0, 10)).isEmpty();
        assertThat(collect(tree.rangeIterator(0, 10))).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(ints = {2, 3, 4, 5})
    void rangeEmptyWhenStrictlyAboveAllKeys(int maxKeys) {
        var tree = newTree(maxKeys);
        tree.put(1, "a");
        tree.put(2, "b");
        assertThat(tree.range(100, 200)).isEmpty();
        assertThat(collect(tree.rangeIterator(100, 200))).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(ints = {3, 4, 5})
    void rangeFromMinKeyToMaxKeyEqualsFullIterator(int maxKeys) {
        var tree = newTree(maxKeys);
        int[] keys = {7, 2, 8, 0, 5, 3, 1, 6, 4};
        for (int k : keys) {
            tree.put(k, "v" + k);
        }
        var full = collectAll(tree);
        assertThat(tree.range(0, 8)).isEqualTo(full);
        assertThat(collect(tree.rangeIterator(0, 8))).isEqualTo(full);
    }

    @ParameterizedTest
    @ValueSource(ints = {3, 4, 5})
    void rangeMiddleSliceMatchesBoundedIterator(int maxKeys) {
        var tree = newTree(maxKeys);
        int[] keys = {7, 2, 8, 0, 5, 3, 1, 6, 4};
        for (int k : keys) {
            tree.put(k, "v" + k);
        }
        var slice = tree.range(3, 6);
        assertThat(slice).containsExactly(
                KeyVal.of(3, "v3"),
                KeyVal.of(4, "v4"),
                KeyVal.of(5, "v5"),
                KeyVal.of(6, "v6")
        );
        assertThat(collect(tree.rangeIterator(3, 6))).isEqualTo(slice);
    }

    @ParameterizedTest
    @ValueSource(ints = {2, 3, 4, 5})
    void rangePartialOverlapLeftEdge(int maxKeys) {
        var tree = newTree(maxKeys);
        for (int k : new int[]{5, 15, 25}) {
            tree.put(k, "p" + k);
        }
        assertThat(tree.range(0, 10)).containsExactly(KeyVal.of(5, "p5"));
        assertThat(collect(tree.rangeIterator(0, 10))).isEqualTo(tree.range(0, 10));
    }

    @ParameterizedTest
    @ValueSource(ints = {2, 3, 4, 5})
    void rangePartialOverlapRightEdge(int maxKeys) {
        var tree = newTree(maxKeys);
        for (int k : new int[]{5, 15, 25}) {
            tree.put(k, "p" + k);
        }
        assertThat(tree.range(20, 30)).containsExactly(KeyVal.of(25, "p25"));
        assertThat(collect(tree.rangeIterator(20, 30))).isEqualTo(tree.range(20, 30));
    }

    @ParameterizedTest
    @ValueSource(ints = {3, 4, 5})
    void rangeInclusiveMatchesSortedReferenceSubList(int maxKeys) {
        final int keyCount = 100;
        var sortedReference = new ArrayList<KeyVal<Integer, String>>(keyCount);
        for (int k = 0; k < keyCount; k++) {
            sortedReference.add(KeyVal.of(k, "v" + k));
        }

        var tree = newTree(maxKeys);
        for (int k = 0; k < keyCount; k++) {
            assertThat(tree.put(k, "v" + k)).isNull();
        }
        assertThat(collectAll(tree)).isEqualTo(sortedReference);
        TreeStructureAssertions.assertValid(tree, Comparator.naturalOrder());

        final int from = 10;
        final int to = 50;
        List<KeyVal<Integer, String>> expectedSlice = sortedReference.subList(from, to + 1);
        assertThat(expectedSlice).hasSize(to - from + 1);

        var byRange = tree.range(from, to);
        assertThat(byRange).hasSize(to - from + 1);
        assertThat(byRange).isEqualTo(expectedSlice);
        assertThat(collect(tree.rangeIterator(from, to))).isEqualTo(expectedSlice);
    }
}

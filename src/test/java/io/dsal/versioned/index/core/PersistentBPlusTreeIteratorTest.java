package io.dsal.versioned.index.core;

import io.dsal.versioned.index.testsupport.TestKeyFixtures;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PersistentBPlusTreeIteratorTest {

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
    void fullIteratorSnapshotIgnoresPutsAfterCreation(int maxKeys) {
        var tree = newTree(maxKeys);
        for (int k = 1; k <= 4; k++) {
            tree.put(k, "v" + k);
        }
        Iterator<KeyVal<Integer, String>> it = tree.iterator();
        tree.put(5, "v5");
        tree.put(6, "v6");
        assertThat(collect(it)).containsExactly(
                KeyVal.of(1, "v1"),
                KeyVal.of(2, "v2"),
                KeyVal.of(3, "v3"),
                KeyVal.of(4, "v4")
        );
        assertThat(collectAll(tree)).hasSize(6);
    }

    @ParameterizedTest
    @ValueSource(ints = {3, 4, 5})
    void boundedRangeIteratorSnapshotIgnoresLaterPuts(int maxKeys) {
        var tree = newTree(maxKeys);
        int[] keys = {7, 2, 8, 0, 5, 3, 1, 6, 4};
        for (int k : keys) {
            tree.put(k, "v" + k);
        }
        var expectedAtSnapshot = tree.range(3, 6);
        Iterator<KeyVal<Integer, String>> bounded = tree.rangeIterator(3, 6);
        tree.put(100, "extra");
        assertThat(collect(bounded)).isEqualTo(expectedAtSnapshot);
    }

    @ParameterizedTest
    @ValueSource(ints = {2, 3, 4, 5})
    void fullIteratorSnapshotIgnoresRemovesAfterCreation(int maxKeys) {
        var tree = newTree(maxKeys);
        tree.put(1, "a");
        tree.put(2, "b");
        tree.put(3, "c");
        Iterator<KeyVal<Integer, String>> it = tree.iterator();
        tree.remove(2);
        assertThat(collect(it)).containsExactly(
                KeyVal.of(1, "a"),
                KeyVal.of(2, "b"),
                KeyVal.of(3, "c")
        );
        assertThat(collectAll(tree)).containsExactly(KeyVal.of(1, "a"), KeyVal.of(3, "c"));
    }
}

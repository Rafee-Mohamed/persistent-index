package io.dsal.versioned.index.persistent;

import io.dsal.versioned.index.api.Direction;
import io.dsal.versioned.index.api.OrderedVersionedIndex;
import io.dsal.versioned.index.api.OrderedVersionedIndexContractTest;
import io.dsal.versioned.index.persistent.testsupport.BPlusTreeValidator;
import io.dsal.versioned.index.persistent.testsupport.IndexTestSupport;
import io.dsal.versioned.index.persistent.testsupport.TreeTestAccess;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.Map;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

public class PersistentBPlusTreeContractTest extends OrderedVersionedIndexContractTest {

    @Override
    protected Stream<OrderedVersionedIndex<Integer, String>> indices() {
        return IntStream.rangeClosed(2, 8)
                .mapToObj(n -> new PersistentBPlusTree<>(n, IndexTestSupport.integerKeyStorageFactory()));
    }

    private PersistentBPlusTree<Integer, String> newTree(int maxKeys) {
        return new PersistentBPlusTree<>(maxKeys, IndexTestSupport.integerKeyStorageFactory());
    }

    private void assertValid(PersistentBPlusTree<Integer, String> tree) {
        BPlusTreeValidator.validate(
                TreeTestAccess.root(tree),
                IndexTestSupport.INTEGER_COMPARATOR,
                TreeTestAccess.maxKeys(tree),
                TreeTestAccess.minKeys(tree));
    }

    // ---------------------------------------------------------------------------
    // Structural invariants after mutations
    // ---------------------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(ints = {2, 3, 4, 5})
    void structureIsValidAfterSequentialPuts(int maxKeys) {
        var tree = newTree(maxKeys);
        for (int i = 1; i <= 50; i++) {
            tree.put(i, "v" + i);
            assertValid(tree);
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {2, 3, 4, 5})
    void structureIsValidAfterSequentialPutsInReverseOrder(int maxKeys) {
        var tree = newTree(maxKeys);
        for (int i = 50; i >= 1; i--) {
            tree.put(i, "v" + i);
            assertValid(tree);
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {2, 3, 4, 5})
    void structureIsValidAfterSequentialRemoves(int maxKeys) {
        var tree = newTree(maxKeys);
        for (int i = 1; i <= 30; i++) tree.put(i, "v" + i);
        for (int i = 1; i <= 30; i++) {
            tree.remove(i);
            assertValid(tree);
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {2, 3, 4, 5})
    void structureIsValidAfterMixedOps(int maxKeys) {
        var tree = newTree(maxKeys);
        for (int i = 1; i <= 20; i++) tree.put(i, "v" + i);
        for (int i = 1; i <= 10; i++) tree.remove(i);
        for (int i = 1; i <= 10; i++) tree.put(i, "new" + i);
        assertValid(tree);
    }

    // ---------------------------------------------------------------------------
    // Structural: root collapses to null when empty
    // ---------------------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(ints = {2, 3, 4, 5})
    void rootIsNullAfterRemovingAllKeys(int maxKeys) {
        var tree = newTree(maxKeys);
        for (int i = 1; i <= 10; i++) tree.put(i, "v" + i);
        for (int i = 1; i <= 10; i++) tree.remove(i);
        assertThat(TreeTestAccess.root(tree)).isNull();
    }

    // ---------------------------------------------------------------------------
    // Txn structural validity
    // ---------------------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(ints = {2, 3, 4, 5})
    void structureIsValidAfterTxnWithMultipleOps(int maxKeys) {
        var tree = newTree(maxKeys);
        tree.txn(th -> {
            for (int i = 1; i <= 20; i++) th.put(i, "v" + i);
            for (int i = 1; i <= 10; i++) th.remove(i);
        });
        assertValid(tree);
    }

    // ---------------------------------------------------------------------------
    // Copy-on-write: old snapshot is structurally intact after commit
    // ---------------------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(ints = {2, 3, 4, 5})
    void snapshotEntriesAreCorrectAfterManySubsequentCommits(int maxKeys) {
        var tree = newTree(maxKeys);
        for (int i = 1; i <= 10; i++) tree.put(i, "v" + i);
        var snap = tree.snapshot();

        for (int i = 11; i <= 60; i++) tree.put(i, "v" + i);
        for (int i = 1; i <= 5; i++) tree.remove(i);

        var expected = new ArrayList<Map.Entry<Integer, String>>();
        for (int i = 1; i <= 10; i++) expected.add(Map.entry(i, "v" + i));

        var actual = new ArrayList<Map.Entry<Integer, String>>();
        snap.forEach(Direction.ASC, (k, v) -> actual.add(Map.entry(k, v)));
        assertThat(actual).isEqualTo(expected);
    }
}

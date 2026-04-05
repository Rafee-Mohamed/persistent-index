package io.dsal.persistent.index.property.state;

import io.dsal.persistent.index.core.KeyVal;
import io.dsal.persistent.index.core.PersistentBPlusTree;
import io.dsal.persistent.index.layout.KeyStorageFactory;
import io.dsal.persistent.index.testsupport.TreeMapOracle;
import io.dsal.persistent.index.testsupport.TreeStructureAssertions;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class TreeOracleState<K> {
    private final PersistentBPlusTree<K, String> tree;
    private final TreeMapOracle<K, String> oracle;
    private final Comparator<K> comparator;

    public TreeOracleState(int maxKeys, KeyStorageFactory<K> storageFactory, Comparator<K> comparator) {
        this.tree = new PersistentBPlusTree<>(maxKeys, storageFactory);
        this.oracle = new TreeMapOracle<>(comparator);
        this.comparator = comparator;
    }

    public void put(K key, String value) {
        assertThat(tree.put(key, value)).isEqualTo(oracle.put(key, value));
    }

    public void remove(K key) {
        assertThat(tree.remove(key)).isEqualTo(oracle.remove(key));
    }

    public void get(K key) {
        assertThat(tree.get(key)).isEqualTo(oracle.get(key));
    }

    public void range(K from, K to) {
        var treeRange = tree.range(from, to);
        var oracleRange = oracle.rangeInclusive(from, to);
        assertKeyValListsEqual(treeRange, oracleRange);
    }

    public void iterateAndVerify() {
        assertKeyValListsEqual(collectAll(tree), oracle.allEntriesInOrder());
        TreeStructureAssertions.assertValid(tree, comparator);
    }

    public boolean isEmpty() {
        return oracle.isEmpty();
    }

    private List<KeyVal<K, String>> collectAll(PersistentBPlusTree<K, String> t) {
        var out = new ArrayList<KeyVal<K, String>>();
        Iterator<KeyVal<K, String>> it = t.iterator();
        it.forEachRemaining(out::add);
        return out;
    }

    private void assertKeyValListsEqual(List<KeyVal<K, String>> a, List<KeyVal<K, String>> b) {
        assertThat(a).hasSize(b.size());
        for (int i = 0; i < a.size(); i++) {
            assertThat(a.get(i).val()).isEqualTo(b.get(i).val());
            // Need comparator to handle byte[] equality properly since equals checks identity for arrays
            assertThat(comparator.compare(a.get(i).key(), b.get(i).key())).isZero();
        }
    }

    @Override
    public String toString() {
        return "State[size=" + oracle.size() + "]";
    }
}

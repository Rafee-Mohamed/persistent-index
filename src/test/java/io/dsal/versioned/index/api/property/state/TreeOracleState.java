package io.dsal.versioned.index.api.property.state;

import io.dsal.versioned.index.api.Direction;
import io.dsal.versioned.index.api.Entry;
import io.dsal.versioned.index.api.OrderedVersionedIndex;
import io.dsal.versioned.index.api.Range;
import io.dsal.versioned.index.persistent.testsupport.TreeMapOracle;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Paired state of the index under test and a reference {@link TreeMapOracle}.
 * Actions assert that every mutation keeps both in sync.
 *
 * @param <K> key type
 * @param <V> value type
 */
public final class TreeOracleState<K, V> {

    final OrderedVersionedIndex<K, V> index;
    final TreeMapOracle<K, V> oracle;
    private final EntryEquality<K, V> entryEquality;

    public TreeOracleState(
            OrderedVersionedIndex<K, V> index,
            TreeMapOracle<K, V> oracle,
            EntryEquality<K, V> entryEquality) {
        this.index = index;
        this.oracle = oracle;
        this.entryEquality = entryEquality;
    }

    public boolean isEmpty() {
        return oracle.isEmpty();
    }

    public void assertFullScanMatchesOracle() {
        var expected = oracle.allEntries(Direction.ASC);

        var forEachAsc = new ArrayList<Map.Entry<K, V>>();
        index.forEach(Direction.ASC, (k, v) -> forEachAsc.add(Map.entry(k, v)));
        entryEquality.assertEqual(forEachAsc, expected);

        var iterAsc = collect(index.iterator(Direction.ASC));
        entryEquality.assertEqual(iterAsc, expected);

        var expectedDesc = oracle.allEntries(Direction.DESC);

        var forEachDesc = new ArrayList<Map.Entry<K, V>>();
        index.forEach(Direction.DESC, (k, v) -> forEachDesc.add(Map.entry(k, v)));
        entryEquality.assertEqual(forEachDesc, expectedDesc);

        var iterDesc = collect(index.iterator(Direction.DESC));
        entryEquality.assertEqual(iterDesc, expectedDesc);

        assertThat(index.size()).isEqualTo(oracle.size());
    }

    public void assertRangeMatchesOracle(Range<K> range) {
        var expectedAsc = oracle.range(range, Direction.ASC);

        var forEachAsc = new ArrayList<Map.Entry<K, V>>();
        index.forEach(Direction.ASC, range, (k, v) -> forEachAsc.add(Map.entry(k, v)));
        entryEquality.assertEqual(forEachAsc, expectedAsc);

        var iterAsc = collect(index.iterator(Direction.ASC, range));
        entryEquality.assertEqual(iterAsc, expectedAsc);

        var expectedDesc = oracle.range(range, Direction.DESC);

        var forEachDesc = new ArrayList<Map.Entry<K, V>>();
        index.forEach(Direction.DESC, range, (k, v) -> forEachDesc.add(Map.entry(k, v)));
        entryEquality.assertEqual(forEachDesc, expectedDesc);

        var iterDesc = collect(index.iterator(Direction.DESC, range));
        entryEquality.assertEqual(iterDesc, expectedDesc);
    }

    private List<Map.Entry<K, V>> collect(Iterator<? extends Entry<K, V>> it) {
        var out = new ArrayList<Map.Entry<K, V>>();
        it.forEachRemaining(e -> out.add(Map.entry(e.key(), e.value())));
        return out;
    }

    public void assertContainsMatchesOracle(K key) {
        assertThat(index.contains(key)).isEqualTo(oracle.contains(key));
    }

    @Override
    public String toString() {
        return "TreeOracleState[size=" + oracle.size() + "]";
    }

    /**
     * Strategy for comparing entry lists whose keys may not implement {@code equals}
     * (e.g. {@code byte[]}).
     */
    @FunctionalInterface
    public interface EntryEquality<K, V> {
        void assertEqual(List<Map.Entry<K, V>> actual, List<Map.Entry<K, V>> expected);
    }

    /** Entry equality for key types that implement {@code equals} (e.g. {@code Integer}). */
    public static <K, V> EntryEquality<K, V> standardEquality() {
        return (actual, expected) -> assertThat(actual).isEqualTo(expected);
    }

    /**
     * Entry equality that compares entries element-by-element using a key comparator.
     * Use for key types without a meaningful {@code equals} (e.g. {@code byte[]}).
     */
    public static <K, V> EntryEquality<K, V> comparatorEquality(Comparator<K> keyCmp) {
        return (actual, expected) -> {
            assertThat(actual.size()).isEqualTo(expected.size());
            for (int i = 0; i < actual.size(); i++) {
                assertThat(keyCmp.compare(actual.get(i).getKey(), expected.get(i).getKey())).isZero();
                assertThat(actual.get(i).getValue()).isEqualTo(expected.get(i).getValue());
            }
        };
    }
}

package io.dsal.versioned.index.api;

import io.dsal.versioned.index.persistent.testsupport.TreeMapOracle;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Abstract oracle-based test suite for any {@link OrderedVersionedIndex} implementation.
 * Tests all read APIs (get, contains, size, forEach × 2 directions × 4 range types)
 * and all write APIs (put, remove, multi-op txn) against a {@link TreeMapOracle} reference.
 * Structural invariants (e.g. B+ tree node fill) are left to concrete subclasses.
 *
 * @param <K> key type
 * @param <V> value type
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class AbstractOrderedIndexOracleTest<K, V> {

    protected static final long SEED                 = 1L;
    protected static final int  STRESS_OPS           = 20_000;
    protected static final int  FULL_SCAN_EVERY      = 50;
    protected static final int  STRUCTURE_CHECK_EVERY = 400;

    // -------------------------------------------------------------------------
    // Factory methods — concrete subclasses provide
    // -------------------------------------------------------------------------

    protected abstract Stream<OrderedVersionedIndex<K, V>> indices();

    protected abstract TreeMapOracle<K, V> newOracle();

    /** Maps {@code i} to a key; {@code key(a)} precedes {@code key(b)} in key order iff {@code a < b}. */
    protected abstract K key(int i);

    /** Maps {@code i} to a deterministic value. */
    protected abstract V val(int i);

    /** Number of distinct integer inputs to {@link #key}; used to bound random key selection. */
    protected abstract int keySpace();

    /**
     * Asserts that two entry lists are equal. Implementations that use key types
     * without a meaningful {@code equals} (e.g. {@code byte[]}) must override this
     * to compare element-by-element.
     */
    protected abstract void assertEntryListsEqual(
            List<Map.Entry<K, V>> actual, List<Map.Entry<K, V>> expected);

    /** Optional hook: called at periodic checkpoints during stress tests. No-op by default. */
    protected void validateStructure(OrderedVersionedIndex<K, V> index) {}

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    protected final void assertFullScanMatchesOracle(
            OrderedVersionedIndex<K, V> index, TreeMapOracle<K, V> oracle) {
        var treeAsc = new ArrayList<Map.Entry<K, V>>();
        index.forEach(Direction.ASC, (k, v) -> treeAsc.add(Map.entry(k, v)));
        assertEntryListsEqual(treeAsc, oracle.allEntries(Direction.ASC));

        var treeDesc = new ArrayList<Map.Entry<K, V>>();
        index.forEach(Direction.DESC, (k, v) -> treeDesc.add(Map.entry(k, v)));
        assertEntryListsEqual(treeDesc, oracle.allEntries(Direction.DESC));

        assertThat(index.size()).isEqualTo(oracle.size());
    }

    protected final void assertRangeMatchesOracle(
            OrderedVersionedIndex<K, V> index, TreeMapOracle<K, V> oracle, Range<K> range) {
        var treeAsc = new ArrayList<Map.Entry<K, V>>();
        index.forEach(Direction.ASC, range, (k, v) -> treeAsc.add(Map.entry(k, v)));
        assertEntryListsEqual(treeAsc, oracle.range(range, Direction.ASC));

        var treeDesc = new ArrayList<Map.Entry<K, V>>();
        index.forEach(Direction.DESC, range, (k, v) -> treeDesc.add(Map.entry(k, v)));
        assertEntryListsEqual(treeDesc, oracle.range(range, Direction.DESC));
    }

    // -------------------------------------------------------------------------
    // Deterministic scenarios
    // -------------------------------------------------------------------------

    @ParameterizedTest
    @MethodSource("indices")
    void oracleEmptyIndexMatchesOracle(OrderedVersionedIndex<K, V> index) {
        var oracle = newOracle();
        assertFullScanMatchesOracle(index, oracle);
        assertThat(index.size()).isEqualTo(0);
        assertThat(index.contains(key(0))).isFalse();
    }

    @ParameterizedTest
    @MethodSource("indices")
    void oraclePutSequenceAndFullScan(OrderedVersionedIndex<K, V> index) {
        var oracle = newOracle();

        for (int i = 1; i <= 30; i++) {
            assertThat(index.put(key(i), val(i))).isEqualTo(oracle.put(key(i), val(i)));
        }
        assertFullScanMatchesOracle(index, oracle);
        validateStructure(index);
    }

    @ParameterizedTest
    @MethodSource("indices")
    void oracleContainsAndGetMatchAfterPuts(OrderedVersionedIndex<K, V> index) {
        var oracle = newOracle();

        for (int i = 0; i < 20; i++) {
            index.put(key(i), val(i));
            oracle.put(key(i), val(i));
        }
        for (int i = 0; i < 25; i++) {
            assertThat(index.contains(key(i))).isEqualTo(oracle.contains(key(i)));
            assertThat(index.get(key(i))).isEqualTo(oracle.get(key(i)));
        }
    }

    @ParameterizedTest
    @MethodSource("indices")
    void oracleSizeAfterPutsAndRemoves(OrderedVersionedIndex<K, V> index) {
        var oracle = newOracle();

        for (int i = 0; i < 15; i++) {
            index.put(key(i), val(i));
            oracle.put(key(i), val(i));
        }
        assertThat(index.size()).isEqualTo(oracle.size());

        for (int i = 0; i < 8; i++) {
            index.remove(key(i));
            oracle.remove(key(i));
        }
        assertThat(index.size()).isEqualTo(oracle.size());
    }

    @ParameterizedTest
    @MethodSource("indices")
    void oracleReplaceRemoveAndRange(OrderedVersionedIndex<K, V> index) {
        var oracle = newOracle();

        for (int i = 1; i <= 20; i++) {
            index.put(key(i), val(i));
            oracle.put(key(i), val(i));
        }
        for (int i = 1; i <= 20; i += 2) {
            assertThat(index.put(key(i), val(100 + i))).isEqualTo(oracle.put(key(i), val(100 + i)));
        }
        for (int i = 2; i <= 20; i += 2) {
            assertThat(index.remove(key(i))).isEqualTo(oracle.remove(key(i)));
        }

        assertFullScanMatchesOracle(index, oracle);
        assertRangeMatchesOracle(index, oracle, Range.closed(key(5), key(15)));
        assertRangeMatchesOracle(index, oracle, Range.open(key(5), key(15)));
        validateStructure(index);
    }

    @ParameterizedTest
    @MethodSource("indices")
    void oracleAllFourRangeTypesMatchOracle(OrderedVersionedIndex<K, V> index) {
        var oracle = newOracle();

        for (int i = 1; i <= 50; i++) {
            index.put(key(i), val(i));
            oracle.put(key(i), val(i));
        }

        assertRangeMatchesOracle(index, oracle, Range.closed(key(10), key(40)));
        assertRangeMatchesOracle(index, oracle, Range.open(key(10), key(40)));
        assertRangeMatchesOracle(index, oracle, Range.closedOpen(key(10), key(40)));
        assertRangeMatchesOracle(index, oracle, Range.openClosed(key(10), key(40)));
        assertRangeMatchesOracle(index, oracle, Range.closed(key(40), key(10))); // inverted → empty
        validateStructure(index);
    }

    @ParameterizedTest
    @MethodSource("indices")
    void oracleMultiOpTxnIsAtomic(OrderedVersionedIndex<K, V> index) {
        var oracle = newOracle();

        for (int i = 0; i < 10; i++) {
            index.put(key(i), val(i));
            oracle.put(key(i), val(i));
        }

        index.txn((TxnAction<K, V, RuntimeException>) th -> {
            for (int i = 0; i < 5; i++) th.remove(key(i));
            for (int i = 10; i < 15; i++) th.put(key(i), val(i));
        });
        for (int i = 0; i < 5; i++) oracle.remove(key(i));
        for (int i = 10; i < 15; i++) oracle.put(key(i), val(i));

        assertFullScanMatchesOracle(index, oracle);
        validateStructure(index);
    }

    // -------------------------------------------------------------------------
    // Seeded random stress
    // -------------------------------------------------------------------------

    @ParameterizedTest
    @MethodSource("indices")
    void oracleSeededRandomStress(OrderedVersionedIndex<K, V> index) {
        var rng    = new Random(SEED);
        var oracle = newOracle();

        for (int op = 0; op < STRESS_OPS; op++) {
            int n    = rng.nextInt(keySpace());
            K   key  = key(n);
            int roll = rng.nextInt(100);

            if (roll < 48) {
                V v = val(op);
                assertThat(index.put(key, v)).isEqualTo(oracle.put(key, v));

            } else if (roll < 60) {
                if (!oracle.isEmpty()) {
                    var entries = oracle.allEntries(Direction.ASC);
                    K removeKey = entries.get(rng.nextInt(entries.size())).getKey();
                    assertThat(index.remove(removeKey)).isEqualTo(oracle.remove(removeKey));
                } else {
                    V v = val(op);
                    assertThat(index.put(key, v)).isEqualTo(oracle.put(key, v));
                }

            } else if (roll < 75) {
                assertThat(index.get(key)).isEqualTo(oracle.get(key));

            } else if (roll < 85) {
                assertThat(index.contains(key)).isEqualTo(oracle.contains(key));

            } else {
                int n2   = rng.nextInt(keySpace());
                K from   = key(Math.min(n, n2));
                K to     = key(Math.max(n, n2));
                int type = rng.nextInt(4);
                Range<K> range = switch (type) {
                    case 0 -> Range.closed(from, to);
                    case 1 -> Range.open(from, to);
                    case 2 -> Range.closedOpen(from, to);
                    default -> Range.openClosed(from, to);
                };
                assertRangeMatchesOracle(index, oracle, range);
            }

            if (op % FULL_SCAN_EVERY == 0) {
                assertFullScanMatchesOracle(index, oracle);
            }
            if (op % STRUCTURE_CHECK_EVERY == 0) {
                validateStructure(index);
            }
        }

        assertFullScanMatchesOracle(index, oracle);
        validateStructure(index);
    }
}

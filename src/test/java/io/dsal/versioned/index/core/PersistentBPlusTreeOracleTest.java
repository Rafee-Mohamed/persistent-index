package io.dsal.versioned.index.core;

import io.dsal.versioned.index.layout.ArrayKeyStorageFactory;
import io.dsal.versioned.index.testsupport.TestKeyFixtures;
import io.dsal.versioned.index.testsupport.TreeMapOracle;
import io.dsal.versioned.index.testsupport.TreeStructureAssertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Oracle tests: same operations on {@link PersistentBPlusTree} and a {@link TreeMapOracle};
 * behavior must agree (get/put/remove returns, full order, inclusive range).
 */
class PersistentBPlusTreeOracleTest {

    /** Fixed seed: same JVM + same test body → same op sequence (replay for debugging). */
    private static final long ORACLE_STRESS_SEED = 0xB05C15EED0B5EL;

    private static final long ORACLE_STRESS_BYTE_SEED = 0xB17E5EEDFA11L;

    private static final int STRESS_STEPS = 20_000;
    private static final int STRESS_KEY_SPACE = 64;
    /** Max length of a random stress key (length is {@code 1..maxLen} inclusive). */
    private static final int STRESS_BYTE_KEY_MAX_LEN = 64;
    private static final int FULL_SCAN_EVERY = 50;
    private static final int STRUCTURE_CHECK_EVERY = 400;

    private static PersistentBPlusTree<Integer, String> newTree(int maxKeys) {
        return new PersistentBPlusTree<>(maxKeys, TestKeyFixtures.integerArrayKeyStorageFactory());
    }

    /** {@link io.dsal.versioned.index.layout.PackedByteKeyStorage} + lexicographic order (same as {@link TreeMapOracle}). */
    private static PersistentBPlusTree<byte[], String> newByteTree(int maxKeys) {
        return new PersistentBPlusTree<>(
                maxKeys,
                TestKeyFixtures.lexicographicByteKeyStorageFactory()
        );
    }

    private static List<KeyVal<Integer, String>> collectAll(PersistentBPlusTree<Integer, String> tree) {
        var out = new ArrayList<KeyVal<Integer, String>>();
        Iterator<KeyVal<Integer, String>> it = tree.iterator();
        it.forEachRemaining(out::add);
        return out;
    }

    private static List<KeyVal<byte[], String>> collectAllBytes(PersistentBPlusTree<byte[], String> tree) {
        var out = new ArrayList<KeyVal<byte[], String>>();
        Iterator<KeyVal<byte[], String>> it = tree.iterator();
        it.forEachRemaining(out::add);
        return out;
    }

    private static void assertTreeMatchesOracle(
            PersistentBPlusTree<Integer, String> tree,
            TreeMapOracle<Integer, String> oracle
    ) {
        assertThat(collectAll(tree)).isEqualTo(oracle.allEntriesInOrder());
    }

    /** {@link KeyVal} uses reference equality for {@code byte[]} keys; AssertJ compares array contents in {@link #assertThat}. */
    private static void assertByteKeyValListsEqual(List<KeyVal<byte[], String>> a, List<KeyVal<byte[], String>> b) {
        assertThat(a).hasSize(b.size());
        for (int i = 0; i < a.size(); i++) {
            assertThat(a.get(i).val()).isEqualTo(b.get(i).val());
            assertThat(a.get(i).key()).as("key at index %d", i).isEqualTo(b.get(i).key());
        }
    }

    private static void assertByteTreeMatchesOracle(
            PersistentBPlusTree<byte[], String> tree,
            TreeMapOracle<byte[], String> oracle
    ) {
        assertByteKeyValListsEqual(collectAllBytes(tree), oracle.allEntriesInOrder());
    }

    /** Uniform non-empty key: length in {@code [1, maxLen]}, arbitrary bytes. */
    private static byte[] randomNonEmptyKey(Random rng, int maxLen) {
        int len = 1 + rng.nextInt(maxLen);
        byte[] k = new byte[len];
        rng.nextBytes(k);
        return k;
    }

    @ParameterizedTest
    @ValueSource(ints = {2, 3, 4, 5})
    void oracleEmptyTree(int maxKeys) {
        var tree = newTree(maxKeys);
        var oracle = new TreeMapOracle<Integer, String>(Comparator.naturalOrder());
        assertThat(tree.get(1)).isNull();
        assertThat(oracle.get(1)).isNull();
        assertThat(tree.range(0, 10)).isEmpty();
        assertThat(oracle.rangeInclusive(0, 10)).isEmpty();
        assertThat(collectAll(tree)).isEmpty();
        assertThat(oracle.allEntriesInOrder()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(ints = {2, 3, 4, 5})
    void oraclePutSequenceAndFullScan(int maxKeys) {
        var tree = newTree(maxKeys);
        var oracle = new TreeMapOracle<Integer, String>(Comparator.naturalOrder());
        int[] insertOrder = {5, 2, 8, 1, 9};
        for (int k : insertOrder) {
            String v = "v" + k;
            assertThat(tree.put(k, v)).isEqualTo(oracle.put(k, v));
        }
        assertTreeMatchesOracle(tree, oracle);
        TreeStructureAssertions.assertValid(tree, Comparator.naturalOrder());
    }

    @ParameterizedTest
    @ValueSource(ints = {2, 3, 4, 5})
    void oracleReplaceRemoveAndRange(int maxKeys) {
        var tree = newTree(maxKeys);
        var oracle = new TreeMapOracle<Integer, String>(Comparator.naturalOrder());
        assertThat(tree.put(1, "a")).isNull();
        assertThat(oracle.put(1, "a")).isNull();
        assertThat(tree.put(3, "c")).isNull();
        assertThat(oracle.put(3, "c")).isNull();
        assertThat(tree.put(1, "b")).isEqualTo(oracle.put(1, "b"));
        assertThat(tree.get(1)).isEqualTo(oracle.get(1));
        assertThat(tree.range(1, 3)).containsExactly(KeyVal.of(1, "b"), KeyVal.of(3, "c"));
        assertThat(oracle.rangeInclusive(1, 3)).containsExactly(KeyVal.of(1, "b"), KeyVal.of(3, "c"));
        assertThat(tree.range(5, 1)).isEmpty();
        assertThat(oracle.rangeInclusive(5, 1)).isEmpty();
        assertThat(tree.remove(3)).isEqualTo(oracle.remove(3));
        assertThat(tree.remove(99)).isEqualTo(oracle.remove(99));
        assertTreeMatchesOracle(tree, oracle);
        TreeStructureAssertions.assertValid(tree, Comparator.naturalOrder());
    }

    @ParameterizedTest
    @ValueSource(ints = {2, 3, 4, 5})
    void oracleSequentialPutsRangeMatchesSubList(int maxKeys) {
        var tree = newTree(maxKeys);
        var oracle = new TreeMapOracle<Integer, String>(Comparator.naturalOrder());
        final int n = 50;
        for (int k = 0; k < n; k++) {
            String v = "v" + k;
            assertThat(tree.put(k, v)).isEqualTo(oracle.put(k, v));
        }
        assertTreeMatchesOracle(tree, oracle);
        // Inclusive [10, 50] with keys only 0..49 → actual keys 10..49 (40 entries) → subList(10, 50)
        var expectedMid = oracle.allEntriesInOrder().subList(10, 50);
        assertThat(tree.range(10, 50)).isEqualTo(expectedMid);
        assertThat(oracle.rangeInclusive(10, 50)).isEqualTo(expectedMid);
        TreeStructureAssertions.assertValid(tree, Comparator.naturalOrder());
    }

    /**
     * Seeded random ops on tree + {@link TreeMapOracle}: {@code put}, {@code get},
     * {@code remove}, and inclusive {@code range} — same dual-apply as production use.
     * Periodic full scan vs oracle; structure check on the last step (and when step aligns
     * with {@link #STRUCTURE_CHECK_EVERY}).
     */
    @ParameterizedTest
    @ValueSource(ints = {2, 3, 4, 5})
    void oracleSeededRandomStressWithPeriodicFullScan(int maxKeys) {
        var rng = new Random(ORACLE_STRESS_SEED);
        var tree = newTree(maxKeys);
        var oracle = new TreeMapOracle<Integer, String>(Comparator.naturalOrder());
        var cmp = Comparator.<Integer>naturalOrder();

        for (int step = 0; step < STRESS_STEPS; step++) {
            int roll = rng.nextInt(100);
            if (roll < 48) {
                int k = rng.nextInt(STRESS_KEY_SPACE);
                String v = "v" + rng.nextInt(10_000);
                assertThat(tree.put(k, v)).isEqualTo(oracle.put(k, v));
            } else if (roll < 73) {
                int k = rng.nextInt(STRESS_KEY_SPACE);
                assertThat(tree.get(k)).isEqualTo(oracle.get(k));
            } else if (roll < 85) {
                if (!oracle.isEmpty()) {
                    var entries = oracle.allEntriesInOrder();
                    int k = entries.get(rng.nextInt(entries.size())).key();
                    assertThat(tree.remove(k)).isEqualTo(oracle.remove(k));
                } else {
                    int k = rng.nextInt(STRESS_KEY_SPACE);
                    String v = "v" + rng.nextInt(10_000);
                    assertThat(tree.put(k, v)).isEqualTo(oracle.put(k, v));
                }
            } else {
                int a = rng.nextInt(STRESS_KEY_SPACE);
                int b = rng.nextInt(STRESS_KEY_SPACE);
                assertThat(tree.range(a, b)).isEqualTo(oracle.rangeInclusive(a, b));
            }

            if (step % FULL_SCAN_EVERY == FULL_SCAN_EVERY - 1 || step == STRESS_STEPS - 1) {
                assertTreeMatchesOracle(tree, oracle);
            }
            if (step % STRUCTURE_CHECK_EVERY == STRUCTURE_CHECK_EVERY - 1 || step == STRESS_STEPS - 1) {
                TreeStructureAssertions.assertValid(tree, cmp);
            }
        }
    }

    /**
     * Same stress shape as {@link #oracleSeededRandomStressWithPeriodicFullScan(int)} for
     * {@link io.dsal.versioned.index.layout.PackedByteKeyStorage}: variable-length non-empty
     * keys, lexicographic order shared with {@link TreeMapOracle}. Removes pick an existing
     * key from the oracle when the map is non-empty.
     */
    @ParameterizedTest
    @ValueSource(ints = {2, 3, 4, 5})
    void oracleSeededRandomStressWithPeriodicFullScanLexicographicByteKeys(int maxKeys) {
        var rng = new Random(ORACLE_STRESS_BYTE_SEED);
        var tree = newByteTree(maxKeys);
        var cmp = TestKeyFixtures.byteArrayLexicographicOrder();
        var oracle = new TreeMapOracle<byte[], String>(cmp);

        var ops = new ArrayList<String>();

        for (int step = 0; step < STRESS_STEPS; step++) {
            int roll = rng.nextInt(100);
            if (roll < 48) {
                byte[] k = randomNonEmptyKey(rng, STRESS_BYTE_KEY_MAX_LEN);
                String v = "v" + rng.nextInt(10_000);
                assertThat(tree.put(k, v)).isEqualTo(oracle.put(k, v));
                ops.add(step + ": put=" + Arrays.toString(k));
            } else if (roll < 73) {
                byte[] k = randomNonEmptyKey(rng, STRESS_BYTE_KEY_MAX_LEN);
                assertThat(tree.get(k)).isEqualTo(oracle.get(k));

            } else if (roll < 85) {
                if (!oracle.isEmpty()) {
                    var entries = oracle.allEntriesInOrder();
                    byte[] k = entries.get(rng.nextInt(entries.size())).key();
                    assertThat(tree.remove(k)).isEqualTo(oracle.remove(k));
                    ops.add(step + ": remove=" + Arrays.toString(k));
                } else {
                    byte[] k = randomNonEmptyKey(rng, STRESS_BYTE_KEY_MAX_LEN);
                    String v = "v" + rng.nextInt(10_000);
                    assertThat(tree.put(k, v)).isEqualTo(oracle.put(k, v));
                    ops.add(step + ": remove=" + Arrays.toString(k));
                }
            } else {
                byte[] from = randomNonEmptyKey(rng, STRESS_BYTE_KEY_MAX_LEN);
                byte[] to = randomNonEmptyKey(rng, STRESS_BYTE_KEY_MAX_LEN);
                assertByteKeyValListsEqual(tree.range(from, to), oracle.rangeInclusive(from, to));
            }


            if (step % FULL_SCAN_EVERY == FULL_SCAN_EVERY - 1 || step == STRESS_STEPS - 1) {
                assertByteTreeMatchesOracle(tree, oracle);
            }
            if (step % STRUCTURE_CHECK_EVERY == STRUCTURE_CHECK_EVERY - 1 || step == STRESS_STEPS - 1) {
                TreeStructureAssertions.assertValid(tree, cmp);
            }
        }
    }
}

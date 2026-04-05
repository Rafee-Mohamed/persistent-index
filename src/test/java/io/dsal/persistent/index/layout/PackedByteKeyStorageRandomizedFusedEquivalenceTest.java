package io.dsal.persistent.index.layout;

import io.dsal.persistent.index.testsupport.KeyStorageTestSupport;
import org.junit.jupiter.api.Test;
import org.opentest4j.AssertionFailedError;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Fused {@link PackedByteKeyStorage} must match the sequential compositions defined on
 * {@link KeyStorage} (e.g. {@code insert().split()}, {@code remove().insert()}). Variable-length
 * keys, length &gt; 0. Indices follow each operation’s range in the <em>current</em> key sequence
 * (after a prior op, indices refer to the new length).
 */
class PackedByteKeyStorageRandomizedFusedEquivalenceTest {

    private static final long BASE_SEED = 0xF055EEDED0F0L;

    private static final int MIN_KEYS = 1;
    /** {@link #removeAndInsertMatchesRemoveThenInsert}: after remove at least one key must remain. */
    private static final int MIN_KEYS_REMOVE = 2;

    private static final int MAX_KEYS = 48;
    private static final int REPS_PER_SIZE = 8;

    private static final int MAX_KEY_LEN = 96;

    private static byte[] randomKey(Random rng, int maxLen) {
        int len = 1 + rng.nextInt(maxLen);
        byte[] k = new byte[len];
        rng.nextBytes(k);
        return k;
    }

    /** {@code n >= 1} arbitrary non-empty keys in slot order. */
    private static PackedByteKeyStorage storage(Random rng, int n) {
        var slices = new byte[n][];
        for (int i = 0; i < n; i++) {
            slices[i] = randomKey(rng, MAX_KEY_LEN);
        }
        return KeyStorageTestSupport.packedSorted(slices);
    }

    /**
     * {@code splitIdx} for {@code insert().split(splitIdx)} / {@code splitAround(splitIdx)} after
     * insert: post-insert size is {@code n + 1}; valid split indices are {@code 1..n} (separator
     * / promoted key must be an existing key index in that row).
     */
    private static int randomSplitIdxAfterInsert(Random rng, int nPreInsert) {
        return 1 + rng.nextInt(nPreInsert);
    }

    /**
     * Same numeric {@code insertIdx}, {@code splitIdx} as the sequential path. The fused
     * {@code insertAndSplitAround} fast path for {@code insertIdx == splitIdx == n} uses
     * {@code copy(n, size())}, which is not a valid key range; resample so that case does not
     * occur (still within {@code 1..n} for {@code splitIdx}).
     */
    private static void randomInsertAndSplitAroundPair(Random rng, int n, int[] outInsertIdx, int[] outSplitIdx) {
        int insertIdx = rng.nextInt(n + 1);
        int splitIdx = randomSplitIdxAfterInsert(rng, n);
        if (insertIdx == splitIdx && splitIdx == n) {
            if (n == 1) {
                insertIdx = 0;
            } else {
                splitIdx = n - 1;
            }
        }
        outInsertIdx[0] = insertIdx;
        outSplitIdx[0] = splitIdx;
    }

    @Test
    void insertAndSplitMatchesInsertThenSplit() {
        var rng = new Random(BASE_SEED ^ 0x1111L);
        for (int nKeys = MIN_KEYS; nKeys <= MAX_KEYS; nKeys++) {
            for (int r = 0; r < REPS_PER_SIZE; r++) {
                PackedByteKeyStorage ks = storage(rng, nKeys);
                int n = ks.size();
                int insertIdx = rng.nextInt(n + 1);
                int splitIdx = randomSplitIdxAfterInsert(rng, n);
                byte[] key = randomKey(rng, MAX_KEY_LEN);

                var fused = ks.insertAndSplit(insertIdx, splitIdx, key);
                var naive = ks.insert(insertIdx, key).split(splitIdx);

                KeyStorageTestSupport.assertByteKeysEqual(fused.left(), naive.left());
                KeyStorageTestSupport.assertByteKeysEqual(fused.right(), naive.right());
                assertThat(fused.promotedKey()).isEqualTo(naive.promotedKey());
            }
        }
    }

    @Test
    void insertAndSplitAroundMatchesInsertThenSplitAround() {
        var rng = new Random(BASE_SEED ^ 0x2222L);
        var insertIdxBuf = new int[1];
        var splitIdxBuf = new int[1];
        for (int nKeys = MIN_KEYS; nKeys <= MAX_KEYS; nKeys++) {
            for (int r = 0; r < REPS_PER_SIZE; r++) {
                PackedByteKeyStorage ks = storage(rng, nKeys);
                int n = ks.size();
                randomInsertAndSplitAroundPair(rng, n, insertIdxBuf, splitIdxBuf);
                int insertIdx = insertIdxBuf[0];
                int splitIdx = splitIdxBuf[0];
                byte[] key = randomKey(rng, MAX_KEY_LEN);

                var fused = ks.insertAndSplitAround(insertIdx, splitIdx, key);
                var naive = ks.insert(insertIdx, key).splitAround(splitIdx);

                KeyStorageTestSupport.assertByteKeysEqual(fused.left(), naive.left());
                KeyStorageTestSupport.assertByteKeysEqual(fused.right(), naive.right());
                assertThat(fused.promotedKey()).isEqualTo(naive.promotedKey());
            }
        }
    }

    /**
     * Oracle: {@code remove(removeIdx).insert(insertIdx, key)} — {@code insertIdx} is an index into
     * the sequence <em>after</em> {@code remove} (size {@code n - 1}), so {@code insertIdx} in
     * {@code [0, n - 1]} inclusive per {@link KeyStorage#insert(int, Object)}.
     */
    @Test
    void removeAndInsertMatchesRemoveThenInsert() {
        var rng = new Random(BASE_SEED ^ 0x3333L);
        for (int nKeys = MIN_KEYS_REMOVE; nKeys <= MAX_KEYS; nKeys++) {
            for (int r = 0; r < REPS_PER_SIZE; r++) {
                PackedByteKeyStorage ks = storage(rng, nKeys);
                int n = ks.size();
                int removeIdx = rng.nextInt(n);
                int insertIdx = rng.nextInt(n);
                byte[] key = randomKey(rng, MAX_KEY_LEN);

                var naive = ks.remove(removeIdx).insert(insertIdx, key);
                var fused = ks.removeAndInsert(removeIdx, insertIdx, key);
                try {
                    KeyStorageTestSupport.assertByteKeysEqual(fused, naive);
                } catch (AssertionFailedError e) {
                    var list = new ArrayList<Integer>();
                    for (var i = 0; i < n; i++)
                        list.add(ks.key(i).length);
                    System.out.println("-----------");
                    System.out.println(list);
                    System.out.println("removeIdx :"+ removeIdx + " " + "insertIdx: " + insertIdx + " key len: " + key.length);
                    System.out.println("-----------");
                    throw e;
                }

            }
        }
    }

    @Test
    void insertAndMergeMatchesInsertThenMerge() {
        var rng = new Random(BASE_SEED ^ 0x4444L);
        for (int nKeys = 6; nKeys <= MAX_KEYS; nKeys++) {
            for (int r = 0; r < REPS_PER_SIZE; r++) {
                PackedByteKeyStorage ks = storage(rng, nKeys);
                var a = KeyStorageTestSupport.packedSorted(
                        new byte[][] {KeyStorageTestSupport.keySlice(ks.key(0)), KeyStorageTestSupport.keySlice(ks.key(1))});
                var b = KeyStorageTestSupport.packedSorted(
                        new byte[][] {KeyStorageTestSupport.keySlice(ks.key(4)), KeyStorageTestSupport.keySlice(ks.key(5))});
                byte[] sep = KeyStorageTestSupport.keySlice(ks.key(2));

                var fused = a.insertAndMerge(2, sep, b);
                var naive = a.insert(2, sep).merge(b);
                KeyStorageTestSupport.assertByteKeysEqual(fused, naive);
            }
        }
    }
}

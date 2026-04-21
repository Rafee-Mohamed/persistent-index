package io.dsal.versioned.index.layout;

import io.dsal.versioned.index.testsupport.KeyStorageTestSupport;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.stream.Stream;

import static io.dsal.versioned.index.testsupport.KeyStorageTestSupport.LEXICOGRAPHIC_BYTE;
import static io.dsal.versioned.index.testsupport.KeyStorageTestSupport.incompatibleMergeArg;
import static io.dsal.versioned.index.testsupport.KeyStorageTestSupport.keySlice;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Re-runs {@link KeyStorage} contract-style checks for {@link PackedByteKeyStorage} across
 * many lexicographically sorted, variable-length key sequences (UTF-8 text, mixed-length
 * binary, single-byte runs, prefix chains). Every key slice is non-empty: a non-empty
 * {@link KeyStorage} in this project always holds at least one key, and each stored key
 * is non-empty; empty key slices are not modeled here and would invite spurious failures.
 *
 * <p>All scenarios below use {@link #packedProfiles()}: fused helpers are compared to the same
 * {@code insert} then {@code split} / {@code splitAround} composition on every profile.</p>
 */
class PackedByteKeyStorageVariableLengthContractTest {

    /**
     * Each row: profile name, sorted storage with at least six keys (merge uses first two and
     * last two), and a key that in lexicographic order lies between {@code key(1)} and
     * {@code key(2)} (so {@code insert(2, key)} preserves sorted order). That same key is used with
     * {@code split(3)} / {@code splitAround(3)} in tests (indices after the insert).
     */
    static Stream<Arguments> packedProfiles() {
        return Stream.of(
                Arguments.of(
                        "single_byte_1_to_6",
                        KeyStorageTestSupport.packedSortedSingleByteRun(1, 6),
                        new byte[] {4}),
                Arguments.of(
                        "utf8_words",
                        KeyStorageTestSupport.packedSortedUtf8(
                                "alpha", "beta", "gamma", "omega", "pi", "psi"),
                        "delta".getBytes(StandardCharsets.UTF_8)),
                Arguments.of(
                        "ascii_prefix_chain",
                        KeyStorageTestSupport.packedSortedUtf8("a", "aa", "b", "bb", "c", "d"),
                        "ab".getBytes(StandardCharsets.UTF_8)),
                Arguments.of(
                        "mixed_binary_lengths",
                        KeyStorageTestSupport.packedSorted(
                                new byte[][] {
                                    {1, 2},
                                    {1, 3},
                                    {2},
                                    {3},
                                    {4},
                                    {5}
                                }),
                        new byte[] {1, 4}),
                Arguments.of(
                        "four_byte_chunks",
                        KeyStorageTestSupport.packedSorted(
                                new byte[][] {
                                    {1, 2, 3, 4},
                                    {1, 2, 3, 5},
                                    {2, 0, 0, 0},
                                    {3, 0, 0, 0},
                                    {4, 0, 0, 0},
                                    {5, 0, 0, 0}
                                }),
                        new byte[] {1, 2, 3, 6}),
                Arguments.of(
                        "non_empty_prefix_chain",
                        KeyStorageTestSupport.packedSorted(
                                new byte[][] {
                                    {1},
                                    {1, 1},
                                    {1, 2},
                                    {2},
                                    {3},
                                    {4}
                                }),
                        new byte[] {1, 1, 0}));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("packedProfiles")
    void boundsOnCompareAndKey(String name, PackedByteKeyStorage ks, byte[] keyLexicographicallyBetweenKeyAtIndex1AndKeyAtIndex2) {
        assertThat(ks.compare(1, ks.key(1))).isZero();
        assertThat(ks.compare(1, ks.key(0))).isPositive();
        assertThatThrownBy(() -> ks.compare(99, new byte[] {0})).isInstanceOf(IndexOutOfBoundsException.class);
        assertThatThrownBy(() -> ks.key(99)).isInstanceOf(IndexOutOfBoundsException.class);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("packedProfiles")
    void insertRemoveReplace(String name, PackedByteKeyStorage ks, byte[] keyLexicographicallyBetweenKeyAtIndex1AndKeyAtIndex2) {
        var ins = ks.insert(1, keyLexicographicallyBetweenKeyAtIndex1AndKeyAtIndex2);
        assertThat(ins.size()).isEqualTo(ks.size() + 1);
        var rem = ins.remove(2);
        assertThat(rem.size()).isEqualTo(ks.size());
        var rep = rem.replace(0, keySlice(ks.key(0)));
        assertThat(Arrays.equals(rep.key(0), ks.key(0))).isTrue();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("packedProfiles")
    void splitPromotedKeyIsRightHead(String name, PackedByteKeyStorage ks, byte[] keyLexicographicallyBetweenKeyAtIndex1AndKeyAtIndex2) {
        var sp = ks.split(2);
        assertThat(sp.left().size()).isEqualTo(2);
        assertThat(sp.right().size()).isEqualTo(ks.size() - 2);
        assertThat(sp.promotedKey()).isEqualTo(sp.right().key(0));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("packedProfiles")
    void mergeConcat(String name, PackedByteKeyStorage ks, byte[] keyLexicographicallyBetweenKeyAtIndex1AndKeyAtIndex2) {
        var a = KeyStorageTestSupport.packedSorted(
                new byte[][] {keySlice(ks.key(0)), keySlice(ks.key(1))});
        var b = KeyStorageTestSupport.packedSorted(
                new byte[][] {keySlice(ks.key(4)), keySlice(ks.key(5))});
        var m = a.merge(b);
        assertThat(m.size()).isEqualTo(4);
        assertThat(Arrays.equals(m.key(3), ks.key(5))).isTrue();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("packedProfiles")
    void mergeRejectsIncompatibleType(String name, PackedByteKeyStorage ks, byte[] keyLexicographicallyBetweenKeyAtIndex1AndKeyAtIndex2) {
        var a = KeyStorageTestSupport.packedSorted(
                new byte[][] {keySlice(ks.key(0)), keySlice(ks.key(1))});
        var ar = new ArrayKeyStorage<>(new Integer[] {1, 2}, Integer::compareTo);
        assertThatThrownBy(() -> a.merge(incompatibleMergeArg(ar))).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("packedProfiles")
    void splitAroundSameChildPartitionsAsRemoveSplitPromotedIsKeyAtIdx(
            String name, PackedByteKeyStorage ks, byte[] keyLexicographicallyBetweenKeyAtIndex1AndKeyAtIndex2) {
        int idx = 2;
        var sp = ks.splitAround(idx);
        var samePartitions = ks.remove(idx).split(idx);
        KeyStorageTestSupport.assertByteKeysEqual(sp.left(), samePartitions.left());
        KeyStorageTestSupport.assertByteKeysEqual(sp.right(), samePartitions.right());
        assertThat(Arrays.equals(sp.promotedKey(), ks.key(idx))).isTrue();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("packedProfiles")
    void insertThenSplitAroundComposition(String name, PackedByteKeyStorage ks, byte[] keyLexicographicallyBetweenKeyAtIndex1AndKeyAtIndex2) {
        int insertIdx = 2;
        int splitIdx = 3;
        var postInsert = ks.insert(insertIdx, keyLexicographicallyBetweenKeyAtIndex1AndKeyAtIndex2);
        var result = postInsert.splitAround(splitIdx);
        assertThat(result.left().size() + result.right().size() + 1).isEqualTo(postInsert.size());
        assertThat(Arrays.equals(result.promotedKey(), postInsert.key(splitIdx))).isTrue();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("packedProfiles")
    void insertThenSplitComposition(String name, PackedByteKeyStorage ks, byte[] keyLexicographicallyBetweenKeyAtIndex1AndKeyAtIndex2) {
        int insertIdx = 2;
        int splitIdx = 3;
        var postInsert = ks.insert(insertIdx, keyLexicographicallyBetweenKeyAtIndex1AndKeyAtIndex2);
        var result = postInsert.split(splitIdx);
        assertThat(result.left().size()).isEqualTo(splitIdx);
        assertThat(result.right().size()).isEqualTo(postInsert.size() - splitIdx);
        assertThat(Arrays.equals(result.promotedKey(), result.right().key(0))).isTrue();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("packedProfiles")
    void insertAndSplitAroundMatchesInsertThenSplitAround(
            String name, PackedByteKeyStorage ks, byte[] keyLexicographicallyBetweenKeyAtIndex1AndKeyAtIndex2) {
        int insertIdx = 2;
        int splitIdx = 3;
        var fused = ks.insertAndSplitAround(insertIdx, splitIdx, keyLexicographicallyBetweenKeyAtIndex1AndKeyAtIndex2);
        var naive = ks.insert(insertIdx, keyLexicographicallyBetweenKeyAtIndex1AndKeyAtIndex2).splitAround(splitIdx);
        KeyStorageTestSupport.assertByteKeysEqual(fused.left(), naive.left());
        KeyStorageTestSupport.assertByteKeysEqual(fused.right(), naive.right());
        assertThat(Arrays.equals(fused.promotedKey(), naive.promotedKey())).isTrue();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("packedProfiles")
    void insertAndSplitMatchesInsertThenSplit(String name, PackedByteKeyStorage ks, byte[] keyLexicographicallyBetweenKeyAtIndex1AndKeyAtIndex2) {
        int insertIdx = 2;
        int splitIdx = 3;
        var fused = ks.insertAndSplit(insertIdx, splitIdx, keyLexicographicallyBetweenKeyAtIndex1AndKeyAtIndex2);
        var naive = ks.insert(insertIdx, keyLexicographicallyBetweenKeyAtIndex1AndKeyAtIndex2).split(splitIdx);
        KeyStorageTestSupport.assertByteKeysEqual(fused.left(), naive.left());
        KeyStorageTestSupport.assertByteKeysEqual(fused.right(), naive.right());
        assertThat(Arrays.equals(fused.promotedKey(), naive.promotedKey())).isTrue();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("packedProfiles")
    void removeAndInsertMatchesRemoveThenInsert(String name, PackedByteKeyStorage ks, byte[] keyLexicographicallyBetweenKeyAtIndex1AndKeyAtIndex2) {
        var probe = keySlice(ks.key(3));
        var expected = ks.remove(1).insert(2, probe);
        KeyStorageTestSupport.assertByteKeysEqual(ks.removeAndInsert(1, 2, probe), expected);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("packedProfiles")
    void insertAndMergeMatchesInsertThenMerge(String name, PackedByteKeyStorage ks, byte[] keyLexicographicallyBetweenKeyAtIndex1AndKeyAtIndex2) {
        var a = KeyStorageTestSupport.packedSorted(
                new byte[][] {keySlice(ks.key(0)), keySlice(ks.key(1))});
        var b = KeyStorageTestSupport.packedSorted(
                new byte[][] {keySlice(ks.key(4)), keySlice(ks.key(5))});
        var sep = keySlice(ks.key(2));
        var m = a.insertAndMerge(2, sep, b);
        var naive = a.insert(2, sep).merge(b);
        KeyStorageTestSupport.assertByteKeysEqual(m, naive);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("packedProfiles")
    void insertAndMergeRejectsIncompatibleOther(String name, PackedByteKeyStorage ks, byte[] keyLexicographicallyBetweenKeyAtIndex1AndKeyAtIndex2) {
        var pb = PackedByteKeyStorage.of(keySlice(ks.key(0)), LEXICOGRAPHIC_BYTE);
        var ar = new ArrayKeyStorage<>(new Integer[] {2}, Integer::compareTo);
        assertThatThrownBy(() -> pb.insertAndMerge(0, new byte[] {0}, incompatibleMergeArg(ar)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

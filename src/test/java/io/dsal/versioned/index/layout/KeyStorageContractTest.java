package io.dsal.versioned.index.layout;

import io.dsal.versioned.index.testsupport.KeyStorageTestSupport;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Arrays;

import static io.dsal.versioned.index.testsupport.KeyStorageTestSupport.LEXICOGRAPHIC_BYTE;
import static io.dsal.versioned.index.testsupport.KeyStorageTestSupport.incompatibleMergeArg;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Shared contract cases for all {@link KeyStorage} implementations used in the
 * tree.
 * One scenario per bucket per representation (array vs packed bytes).
 */
class KeyStorageContractTest {

    private enum Impl {
        ARRAY,
        PACKED
    }

    @ParameterizedTest
    @EnumSource(Impl.class)
    void boundsOnCompareAndKey(Impl impl) {
        switch (impl) {
            case ARRAY -> {
                var ks = KeyStorageTestSupport.arraySorted(1, 2, 3);
                assertThat(ks.compare(1, 0)).isPositive();
                assertThat(ks.compare(1, 2)).isZero();
                assertThatThrownBy(() -> ks.compare(5, 1)).isInstanceOf(IndexOutOfBoundsException.class);
                assertThatThrownBy(() -> ks.key(5)).isInstanceOf(IndexOutOfBoundsException.class);
            }
            case PACKED -> {
                var ks = KeyStorageTestSupport.packedSortedSingleByteRun(1, 3);
                assertThat(ks.compare(1, new byte[] { 0 })).isPositive();
                assertThat(ks.compare(1, new byte[] { 2 })).isZero();
                assertThatThrownBy(() -> ks.compare(5, new byte[] { 1 }))
                        .isInstanceOf(IndexOutOfBoundsException.class);
                assertThatThrownBy(() -> ks.key(5)).isInstanceOf(IndexOutOfBoundsException.class);
            }
        }
    }

    @ParameterizedTest
    @EnumSource(Impl.class)
    void insertRemoveReplace(Impl impl) {
        switch (impl) {
            case ARRAY -> {
                var ks = KeyStorageTestSupport.arraySorted(1, 3, 5);
                var ins = ks.insert(1, 2);
                assertThat(ins.key(0)).isEqualTo(1);
                assertThat(ins.key(1)).isEqualTo(2);
                assertThat(ins.key(2)).isEqualTo(3);
                assertThat(ins.key(3)).isEqualTo(5);
                assertThat(ks.size()).isEqualTo(3);
                var rem = ins.remove(2);
                assertThat(rem.size()).isEqualTo(3);
                assertThat(rem.replace(0, 0).key(0)).isEqualTo(0);
            }
            case PACKED -> {
                var ks = KeyStorageTestSupport.packedSorted(new byte[][] { { 1 }, { 3 }, { 5 } });
                var ins = ks.insert(1, new byte[] { 2 });
                assertThat(ins.size()).isEqualTo(4);
                var rem = ins.remove(2);
                assertThat(rem.size()).isEqualTo(3);
                assertThat(rem.replace(0, new byte[] { 0 }).key(0)).containsExactly((byte) 0);
            }
        }
    }

    @ParameterizedTest
    @EnumSource(Impl.class)
    void splitPromotedKeyIsRightHead(Impl impl) {
        switch (impl) {
            case ARRAY -> {
                var ks = KeyStorageTestSupport.arraySorted(1, 2, 3, 4);
                var sp = ks.split(2);
                assertThat(sp.left().size()).isEqualTo(2);
                assertThat(sp.right().size()).isEqualTo(2);
                assertThat(sp.promotedKey()).isEqualTo(3);
                assertThat(sp.right().key(0)).isEqualTo(3);
            }
            case PACKED -> {
                var ks = KeyStorageTestSupport.packedSortedSingleByteRun(1, 4);
                var sp = ks.split(2);
                assertThat(sp.left().size()).isEqualTo(2);
                assertThat(sp.right().size()).isEqualTo(2);
                assertThat(sp.promotedKey()).isEqualTo(sp.right().key(0));
            }
        }
    }

    @ParameterizedTest
    @EnumSource(Impl.class)
    void mergeConcat(Impl impl) {
        switch (impl) {
            case ARRAY -> {
                var a = KeyStorageTestSupport.arraySorted(1, 2);
                var b = KeyStorageTestSupport.arraySorted(3, 4);
                var m = a.merge(b);
                assertThat(m.size()).isEqualTo(4);
                assertThat(m.key(3)).isEqualTo(4);
            }
            case PACKED -> {
                var a = KeyStorageTestSupport.packedSortedSingleByteRun(1, 2);
                var b = KeyStorageTestSupport.packedSortedSingleByteRun(3, 4);
                var m = a.merge(b);
                assertThat(m.size()).isEqualTo(4);
            }
        }
    }

    @ParameterizedTest
    @EnumSource(Impl.class)
    void mergeRejectsIncompatibleType(Impl impl) {
        switch (impl) {
            case ARRAY -> {
                var a = KeyStorageTestSupport.arraySorted(1, 2);
                var foreign = new PackedByteKeyStorage(new byte[0], new int[] { 0 }, LEXICOGRAPHIC_BYTE);
                assertThatThrownBy(() -> a.merge(incompatibleMergeArg(foreign)))
                        .isInstanceOf(IllegalArgumentException.class);
            }
            case PACKED -> {
                var pb = PackedByteKeyStorage.of(new byte[] { 1 }, LEXICOGRAPHIC_BYTE);
                var ar = new ArrayKeyStorage<>(new Integer[] { 1 }, Integer::compareTo);
                assertThatThrownBy(() -> pb.merge(incompatibleMergeArg(ar)))
                        .isInstanceOf(IllegalArgumentException.class);
            }
        }
    }

    @ParameterizedTest
    @EnumSource(Impl.class)
    void splitAroundSameChildPartitionsAsRemoveSplitPromotedIsKeyAtIdx(Impl impl) {
        switch (impl) {
            case ARRAY -> {
                var ks = KeyStorageTestSupport.arraySorted(1, 2, 3, 4);
                int idx = 2;
                var sp = ks.splitAround(idx);
                var samePartitions = ks.remove(idx).split(idx);
                KeyStorageTestSupport.assertIntegerKeysEqual(sp.left(), samePartitions.left());
                KeyStorageTestSupport.assertIntegerKeysEqual(sp.right(), samePartitions.right());
                assertThat(sp.promotedKey()).isEqualTo(ks.key(idx));
            }
            case PACKED -> {
                var ks = KeyStorageTestSupport.packedSortedSingleByteRun(1, 4);
                int idx = 2;
                var sp = ks.splitAround(idx);
                var samePartitions = ks.remove(idx).split(idx);
                KeyStorageTestSupport.assertByteKeysEqual(sp.left(), samePartitions.left());
                KeyStorageTestSupport.assertByteKeysEqual(sp.right(), samePartitions.right());
                assertThat(Arrays.equals(sp.promotedKey(), ks.key(idx))).isTrue();
            }
        }
    }

    @ParameterizedTest
    @EnumSource(Impl.class)
    void insertAndSplitAroundWhenIndicesDifferMatchesInsertThenSplitAround(Impl impl) {
        switch (impl) {
            case ARRAY -> {
                var ks = KeyStorageTestSupport.arraySorted(1, 3, 5, 7);
                int insertIdx = 2;
                int splitIdx = 3;
                var fused = ks.insertAndSplitAround(insertIdx, splitIdx, 4);
                var naive = ks.insert(insertIdx, 4).splitAround(splitIdx);
                KeyStorageTestSupport.assertIntegerKeysEqual(fused.left(), naive.left());
                KeyStorageTestSupport.assertIntegerKeysEqual(fused.right(), naive.right());
                assertThat(fused.promotedKey()).isEqualTo(naive.promotedKey());
            }
            case PACKED -> {
                var ks = KeyStorageTestSupport.packedSorted(new byte[][] { { 1 }, { 3 }, { 5 }, { 7 } });
                int insertIdx = 2;
                int splitIdx = 3;
                var fused = ks.insertAndSplitAround(insertIdx, splitIdx, new byte[] { 4 });
                var naive = ks.insert(insertIdx, new byte[] { 4 }).splitAround(splitIdx);
                KeyStorageTestSupport.assertByteKeysEqual(fused.left(), naive.left());
                KeyStorageTestSupport.assertByteKeysEqual(fused.right(), naive.right());
            }
        }
    }

    @ParameterizedTest
    @EnumSource(Impl.class)
    void insertAndSplitMatchesInsertThenSplit(Impl impl) {
        switch (impl) {
            case ARRAY -> {
                var ks = KeyStorageTestSupport.arraySorted(1, 3, 5, 7);
                int insertIdx = 2;
                int splitIdx = 3;
                var fused = ks.insertAndSplit(insertIdx, splitIdx, 4);
                var naive = ks.insert(insertIdx, 4).split(splitIdx);
                KeyStorageTestSupport.assertIntegerKeysEqual(fused.left(), naive.left());
                KeyStorageTestSupport.assertIntegerKeysEqual(fused.right(), naive.right());
                assertThat(fused.promotedKey()).isEqualTo(naive.promotedKey());
            }
            case PACKED -> {
                var ks = KeyStorageTestSupport.packedSorted(new byte[][] { { 1 }, { 3 }, { 5 }, { 7 } });
                int insertIdx = 2;
                int splitIdx = 3;
                var fused = ks.insertAndSplit(insertIdx, splitIdx, new byte[] { 4 });
                var naive = ks.insert(insertIdx, new byte[] { 4 }).split(splitIdx);
                KeyStorageTestSupport.assertByteKeysEqual(fused.left(), naive.left());
                KeyStorageTestSupport.assertByteKeysEqual(fused.right(), naive.right());
            }
        }
    }

    @ParameterizedTest
    @EnumSource(Impl.class)
    void removeAndInsertMatchesRemoveThenInsert(Impl impl) {
        switch (impl) {
            case ARRAY -> {
                var ks = KeyStorageTestSupport.arraySorted(1, 2, 3, 4);
                var expected = ks.remove(1).insert(2, 9);
                KeyStorageTestSupport.assertIntegerKeysEqual(ks.removeAndInsert(1, 2, 9), expected);
            }
            case PACKED -> {
                var ks = KeyStorageTestSupport.packedSortedSingleByteRun(1, 4);
                var expected = ks.remove(1).insert(2, new byte[] { 9 });
                KeyStorageTestSupport.assertByteKeysEqual(ks.removeAndInsert(1, 2, new byte[] { 9 }), expected);
            }
        }
    }

    @ParameterizedTest
    @EnumSource(Impl.class)
    void insertAndMergeMatchesInsertThenMerge(Impl impl) {
        switch (impl) {
            case ARRAY -> {
                var a = KeyStorageTestSupport.arraySorted(1, 2);
                var b = KeyStorageTestSupport.arraySorted(5, 6);
                var m = a.insertAndMerge(2, 4, b);
                var naive = a.insert(2, 4).merge(b);
                KeyStorageTestSupport.assertIntegerKeysEqual(m, naive);
            }
            case PACKED -> {
                var a = KeyStorageTestSupport.packedSortedSingleByteRun(1, 2);
                var b = KeyStorageTestSupport.packedSortedSingleByteRun(5, 6);
                var m = a.insertAndMerge(2, new byte[] { 4 }, b);
                var naive = a.insert(2, new byte[] { 4 }).merge(b);
                KeyStorageTestSupport.assertByteKeysEqual(m, naive);
            }
        }
    }

    @ParameterizedTest
    @EnumSource(Impl.class)
    void insertAndMergeRejectsIncompatibleOther(Impl impl) {
        switch (impl) {
            case ARRAY -> {
                var a = KeyStorageTestSupport.arraySorted(1);
                var foreign = new PackedByteKeyStorage(new byte[] { 1 }, new int[] { 0, 1 }, LEXICOGRAPHIC_BYTE);
                assertThatThrownBy(() -> a.insertAndMerge(0, 2, incompatibleMergeArg(foreign)))
                        .isInstanceOf(IllegalArgumentException.class);
            }
            case PACKED -> {
                var pb = PackedByteKeyStorage.of(new byte[] { 1 }, LEXICOGRAPHIC_BYTE);
                var ar = new ArrayKeyStorage<>(new Integer[] { 2 }, Integer::compareTo);
                assertThatThrownBy(() -> pb.insertAndMerge(0, new byte[] { 0 }, incompatibleMergeArg(ar)))
                        .isInstanceOf(IllegalArgumentException.class);
            }
        }
    }
}

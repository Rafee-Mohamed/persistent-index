package io.dsal.versioned.index.testsupport;

import io.dsal.versioned.index.layout.ArrayKeyStorage;
import io.dsal.versioned.index.layout.KeyStorage;
import io.dsal.versioned.index.layout.LexigographicPackedByteComparator;
import io.dsal.versioned.index.layout.PackedByteKeyStorage;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Comparator;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Builds {@link KeyStorage} instances for tests and compares key sequences.
 */
public final class KeyStorageTestSupport {

    public static final Comparator<Integer> INTEGER_NATURAL = Comparator.naturalOrder();
    public static final LexigographicPackedByteComparator LEXICOGRAPHIC_BYTE =
            new LexigographicPackedByteComparator();

    /** Sorted {@link ArrayKeyStorage} over {@link Integer} keys. */
    public static ArrayKeyStorage<Integer> arraySorted(Integer... keys) {
        return new ArrayKeyStorage<>(keys.clone(), INTEGER_NATURAL);
    }

    /**
     * Sorted {@link PackedByteKeyStorage} from distinct key slices (lexicographic order must match slice order).
     */
    public static PackedByteKeyStorage packedSorted(byte[][] keySlices) {
        int total = 0;
        for (byte[] k : keySlices) {
            total += k.length;
        }
        var blob = new byte[total];
        var offs = new int[keySlices.length + 1];
        int p = 0;
        for (int i = 0; i < keySlices.length; i++) {
            System.arraycopy(keySlices[i], 0, blob, p, keySlices[i].length);
            p += keySlices[i].length;
            offs[i + 1] = p;
        }
        return new PackedByteKeyStorage(blob, offs, LEXICOGRAPHIC_BYTE);
    }

    /** Single-byte keys {@code 1..n} in order, for parity with small integer keys in array tests. */
    public static PackedByteKeyStorage packedSortedSingleByteRun(int fromInclusive, int toInclusive) {
        int n = toInclusive - fromInclusive + 1;
        var slices = new byte[n][];
        for (int i = 0; i < n; i++) {
            slices[i] = new byte[]{(byte) (fromInclusive + i)};
        }
        return packedSorted(slices);
    }

    /**
     * Sorted {@link PackedByteKeyStorage} from UTF-8 string keys. Callers must supply
     * strings in strict lexicographic (byte) order for the comparator used by the tree.
     */
    public static PackedByteKeyStorage packedSortedUtf8(String... sortedUtf8Keys) {
        var slices = new byte[sortedUtf8Keys.length][];
        for (int i = 0; i < sortedUtf8Keys.length; i++) {
            slices[i] = sortedUtf8Keys[i].getBytes(StandardCharsets.UTF_8);
        }
        return packedSorted(slices);
    }

    /** Defensive copy of a key slice (for building smaller storages from {@link KeyStorage#key(int)}). */
    public static byte[] keySlice(byte[] key) {
        return key.clone();
    }

    public static void assertIntegerKeysEqual(KeyStorage<Integer> a, KeyStorage<Integer> b) {
        assertThat(a.size()).isEqualTo(b.size());
        for (int i = 0; i < a.size(); i++) {
            assertThat(a.key(i)).isEqualTo(b.key(i));
        }
    }

    public static void assertByteKeysEqual(KeyStorage<byte[]> a, KeyStorage<byte[]> b) {
        assertThat(a.size()).isEqualTo(b.size());
        for (int i = 0; i < a.size(); i++) {
            assertThat(Arrays.equals(a.key(i), b.key(i))).isTrue();
        }
    }

    @SuppressWarnings("unchecked")
    public static <K> KeyStorage<K> incompatibleMergeArg(KeyStorage<?> foreign) {
        return (KeyStorage<K>) (Object) foreign;
    }

    private KeyStorageTestSupport() {}
}

package io.dsal.versioned.index.testsupport;

import io.dsal.versioned.index.layout.ArrayKeyStorageFactory;
import io.dsal.versioned.index.layout.LexigographicPackedByteComparator;
import io.dsal.versioned.index.layout.PackedByteKeyStorageFactory;

import java.util.Comparator;

/**
 * Factories and comparators used across tests.
 */
public final class TestKeyFixtures {

    public static final Comparator<Integer> INTEGER_COMPARATOR = Comparator.naturalOrder();

    /** Same instance as {@link #lexicographicByteKeyStorageFactory()} — use with {@code TreeMap<byte[], V>} reference models. */
    private static final LexigographicPackedByteComparator LEXICOGRAPHIC_PACKED_BYTE =
            new LexigographicPackedByteComparator();

    public static ArrayKeyStorageFactory<Integer> integerArrayKeyStorageFactory() {
        return new ArrayKeyStorageFactory<>(INTEGER_COMPARATOR);
    }

    public static PackedByteKeyStorageFactory lexicographicByteKeyStorageFactory() {
        return new PackedByteKeyStorageFactory(LEXICOGRAPHIC_PACKED_BYTE);
    }

    /**
     * Total order on {@code byte[]} keys consistent with {@link LexigographicPackedByteComparator}
     * on full-array slices ({@code compare(a, 0, a.length, b)}).
     */
    public static Comparator<byte[]> byteArrayLexicographicOrder() {
        return (a, b) -> LEXICOGRAPHIC_PACKED_BYTE.compare(a, 0, a.length, b);
    }

    /** Encodes a non-negative int as 4 big-endian bytes for packed-byte tree tests. */
    public static byte[] intToBytes(int v) {
        return new byte[]{
                (byte) (v >>> 24),
                (byte) (v >>> 16),
                (byte) (v >>> 8),
                (byte) v
        };
    }

    private TestKeyFixtures() {}
}

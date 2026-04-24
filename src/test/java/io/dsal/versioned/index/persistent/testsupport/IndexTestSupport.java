package io.dsal.versioned.index.persistent.testsupport;

import io.dsal.versioned.index.persistent.layout.ArrayKeyStorageFactory;
import io.dsal.versioned.index.persistent.layout.LexigographicPackedByteComparator;
import io.dsal.versioned.index.persistent.layout.PackedByteKeyStorageFactory;

import java.util.Comparator;

public final class IndexTestSupport {

    public static final Comparator<Integer> INTEGER_COMPARATOR = Comparator.naturalOrder();

    private static final LexigographicPackedByteComparator LEXI = new LexigographicPackedByteComparator();

    public static ArrayKeyStorageFactory<Integer> integerKeyStorageFactory() {
        return new ArrayKeyStorageFactory<>(INTEGER_COMPARATOR);
    }

    public static PackedByteKeyStorageFactory byteArrayKeyStorageFactory() {
        return new PackedByteKeyStorageFactory(LEXI);
    }

    public static Comparator<byte[]> byteArrayComparator() {
        return (a, b) -> LEXI.compare(a, 0, a.length, b);
    }

    public static byte[] intToBytes(int v) {
        return new byte[]{
                (byte) (v >>> 24),
                (byte) (v >>> 16),
                (byte) (v >>> 8),
                (byte) v
        };
    }

    private IndexTestSupport() {}
}

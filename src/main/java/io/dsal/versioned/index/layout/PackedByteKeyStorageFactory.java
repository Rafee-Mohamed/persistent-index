package io.dsal.versioned.index.layout;

/**
 * {@link KeyStorageFactory} for {@link PackedByteKeyStorage}: variable-length
 * {@code byte[]} keys in one backing array; ordering for compare operations is
 * delegated to {@link PackedByteComparator}.
 */
public final class PackedByteKeyStorageFactory implements KeyStorageFactory<byte[]> {
    private final PackedByteComparator comparator;

    /**
     * @param comparator slice comparison for keys in packed storage; must be
     *                   consistent with tree search order
     */
    public PackedByteKeyStorageFactory(PackedByteComparator comparator) {
        this.comparator = comparator;
    }

    @Override
    public KeyStorage<byte[]> single(byte[] key) {
        return PackedByteKeyStorage.of(key, comparator);
    }
}

package io.dsal.persistent.index.layout;

public final class PackedByteKeyStorageFactory implements KeyStorageFactory<byte[]> {
    private final PackedByteComparator comparator;

    public PackedByteKeyStorageFactory(PackedByteComparator comparator) {
        this.comparator = comparator;
    }

    @Override
    public KeyStorage<byte[]> single(byte[] key) {
        return PackedByteKeyStorage.of(key, comparator);
    }
}

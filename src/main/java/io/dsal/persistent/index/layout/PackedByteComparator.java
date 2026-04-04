package io.dsal.persistent.index.layout;

@FunctionalInterface
public interface PackedByteComparator {
    int compare(byte[] bytes, int start, int end, byte[] key);
}

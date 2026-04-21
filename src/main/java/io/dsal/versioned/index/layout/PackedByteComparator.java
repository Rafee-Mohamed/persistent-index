package io.dsal.versioned.index.layout;

/**
 * Compares a byte slice {@code [start, end)} in a backing array to a standalone
 * key. Used by {@link PackedByteKeyStorage#compare(int, byte[])} so keys can be
 * stored contiguously without per-key {@code byte[]} objects.
 *
 * <p>Return sign convention matches {@link java.util.Comparator#compare(Object, Object)}
 * for the ordering relation between the slice and {@code key}.</p>
 *
 * @see LexigographicPackedByteComparator
 */
@FunctionalInterface
public interface PackedByteComparator {
    /**
     * @param bytes backing array containing the stored key bytes
     * @param start inclusive start index of the slice
     * @param end   exclusive end index of the slice
     * @param key   external key to compare
     * @return negative, zero, or positive if the slice is less than, equal to, or
     *         greater than {@code key}
     */
    int compare(byte[] bytes, int start, int end, byte[] key);
}

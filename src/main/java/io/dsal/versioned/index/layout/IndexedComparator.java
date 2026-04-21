package io.dsal.versioned.index.layout;

/**
 * Compares a key <em>stored at</em> an index in an ordered sequence against an
 * external key of type {@code K}. Used with {@link KeyStorage} for search and
 * tree navigation without materializing the full sequence as a separate array.
 *
 * <p>The return value follows the same sign convention as
 * {@link java.util.Comparator#compare(Object, Object)}: negative if the stored key
 * is less than {@code key}, zero if equal, positive if greater. Implementations
 * must use the same ordering as {@link KeyStorage#key(int)} at {@code idx}.</p>
 */
public interface IndexedComparator<K> {
    /** Number of keys in the sequence. */
    int size();

    /**
     * Compares the key at {@code idx} to {@code key}.
     *
     * @param idx index in {@code [0, size())}
     * @param key external key to compare
     * @return negative, zero, or positive as the stored key is less than, equal
     *         to, or greater than {@code key}
     */
    int compare(int idx, K key);
}

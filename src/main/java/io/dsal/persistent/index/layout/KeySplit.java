package io.dsal.persistent.index.layout;

/**
 * Outcome of partitioning one node's keys when that node would hold more keys than
 * the tree's maximum (for example right after an insert). The implementation forms
 * two child key ranges and promotes a separator into the parent so search can route
 * between them.
 *
 * <p>{@link #left()} and {@link #right()} are the storages for the two sides; together
 * they contain the same keys as before, none duplicated. {@link #promotedKey()} is
 * the smallest key in {@link #right()} and is inserted into the parent index.
 * Index layout matches {@link KeyStorage#split(int)} and
 * {@link KeyStorage#insertAndSplit(int, int, K)}.</p>
 *
 * @param left        keys for the left child
 * @param right       keys for the right child
 * @param promotedKey first key of {@code right}; parent separator
 * @param <K>         key type
 * @see KeyStorage#split(int)
 */
public record KeySplit<K>(
        KeyStorage<K> left,
        KeyStorage<K> right,
        K promotedKey
) {
}
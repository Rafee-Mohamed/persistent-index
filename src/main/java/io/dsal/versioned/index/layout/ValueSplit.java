package io.dsal.versioned.index.layout;

/**
 * Outcome of partitioning a leaf's values in the same step as {@link KeySplit}: the
 * leaf would exceed the maximum key count, so each value stays with its key on the
 * correct side. Unlike keys, nothing is promoted to the parent (values exist only
 * in leaves).
 *
 * <p>{@link #left()} and {@link #right()} use the same indices as the paired key
 * split. See {@link ValueStorage#insertAndSplit(int, int, V)}.</p>
 *
 * @param left  values aligned with keys in {@link KeySplit#left()}
 * @param right values aligned with keys in {@link KeySplit#right()}
 * @param <V>   value type
 */
public record ValueSplit<V>(
        ValueStorage<V> left,
        ValueStorage<V> right
) {
}

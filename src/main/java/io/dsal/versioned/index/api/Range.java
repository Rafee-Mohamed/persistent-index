package io.dsal.versioned.index.api;

/**
 * Key interval used by ordered range reads.
 *
 * @param from lower bound key
 * @param to upper bound key
 * @param type bound inclusiveness
 * @param <K> key type
 */
public record  Range<K>(
        K from,
        K to,
        RangeType type
) {

    /**
     * Returns an inclusive range [from, to].
     *
     * @param from lower bound key
     * @param to upper bound key
     * @param <K> key type
     * @return range with inclusive bounds
     */
    public static <K> Range<K> closed(K from, K to) {
        return new Range<>(from, to, RangeType.CLOSED);
    }

    /**
     * Returns an exclusive range (from, to).
     *
     * @param from lower bound key
     * @param to upper bound key
     * @param <K> key type
     * @return range with exclusive bounds
     */
    public static <K> Range<K> open(K from, K to) {
        return new Range<>(from, to, RangeType.OPEN);
    }

    /**
     * Returns a half-open range [from, to).
     *
     * @param from lower bound key
     * @param to upper bound key
     * @param <K> key type
     * @return range with inclusive lower and exclusive upper bound
     */
    public static <K> Range<K> closedOpen(K from, K to) {
        return new Range<>(from, to, RangeType.CLOSED_OPEN);
    }

    /**
     * Returns a half-open range (from, to].
     *
     * @param from lower bound key
     * @param to upper bound key
     * @param <K> key type
     * @return range with exclusive lower and inclusive upper bound
     */
    public static <K> Range<K> openClosed(K from, K to) {
        return new Range<>(from, to, RangeType.OPEN_CLOSED);
    }
}

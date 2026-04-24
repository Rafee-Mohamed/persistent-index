package io.dsal.versioned.index.api;

/**
 * Bound inclusiveness for a {@link Range}.
 */
public enum RangeType {
    /**
     * Inclusive lower and upper bounds: [from, to].
     */
    CLOSED,

    /**
     * Exclusive lower and upper bounds: (from, to).
     */
    OPEN,

    /**
     * Inclusive lower bound and exclusive upper bound: [from, to).
     */
    CLOSED_OPEN,

    /**
     * Exclusive lower bound and inclusive upper bound: (from, to].
     */
    OPEN_CLOSED
}

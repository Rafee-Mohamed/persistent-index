package io.dsal.versioned.index.testsupport;

/**
 * Shared fan-out and ordering constants for B+ tree tests.
 * {@code minKeys = maxKeys / 2} matches {@link io.dsal.versioned.index.core.PersistentBPlusTree}.
 */
public final class TestConstants {

    /** Minimum sensible {@code maxKeys} for non-trivial trees (at least one split possible). */
    public static final int MIN_LEGAL_MAX_KEYS = 2;

    /** Typical fan-out values for parameterized tree tests. */
    public static final int[] TYPICAL_MAX_KEYS = {3, 4, 5};

    private TestConstants() {}
}

package io.dsal.versioned.index.persistent.core;

/**
 * Publishes committed tree states.
 *
 * <p>The latest committed state is stored in a {@code volatile} field so readers
 * observe a fully published root and size after each commit.
 *
 * @param <K> key type
 * @param <V> value type
 */
public class StateCommitter<K, V> {
    /**
     * Latest committed state. Written by the committing transaction and read by
     * all threads that call {@link #committed()}; {@code volatile} ensures the
     * full {@link CommittedState} (root pointer and size) is visible to readers
     * after each write without additional synchronization.
     */
    private volatile CommittedState<K, V> cs;

    /**
     * Creates a committer initialized with an empty committed state.
     */
    public StateCommitter() {
        cs = new CommittedState<>();
    }

    /**
     * Returns the current committed state.
     *
     * @return latest committed state
     */
    public CommittedState<K, V> committed() {
        return cs;
    }

    /**
     * Publishes {@code us} as the new committed state.
     *
     * @param us uncommitted state to publish
     */
    public void commit(UncommittedState<K, V> us) {
        cs = new CommittedState<>(us.root(), us.size());
    }
}

package io.dsal.versioned.index.persistent.core;

/**
 * Mutable per-transaction working state derived from one committed state.
 *
 * @param <K> key type
 * @param <V> value type
 */
public final class UncommittedState<K, V> {
    /** Current working tree root; {@code null} for empty working tree. */
    private Node<K, V> root;
    /** Number of key-value pairs in the working tree. */
    private int size;

    /**
     * Creates a working state initialized from committed state {@code cs}.
     *
     * @param cs base committed state
     */
    public UncommittedState(CommittedState<K, V> cs) {
        root = cs.root();
        size = cs.size();
    }

    /**
     * Returns the current working root, or {@code null} if the working tree is empty.
     *
     * @return working tree root
     */
    Node<K, V> root() {
        return root;
    }

    /**
     * Returns the number of entries currently in the working tree.
     *
     * @return working tree size
     */
    int size() {
        return size;
    }

    /**
     * Returns {@code true} if the working tree has no entries.
     *
     * @return {@code true} if empty
     */
    boolean isEmpty() {
       return size == 0;
    }

    /**
     * Sets the working tree root. Pass {@code null} to mark the tree as empty.
     *
     * @param newRoot new working root
     */
    void setRoot(Node<K, V> newRoot) {
        root = newRoot;
    }

    /** Increments the entry count by one; called after a new key is inserted. */
    void increment() {
        size++;
    }

    /** Decrements the entry count by one; called after a key is removed. */
    void decrement() {
        size--;
    }
}

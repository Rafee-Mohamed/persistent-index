package io.dsal.versioned.index.persistent.core;

public final class UncommittedState<K, V> {
    private Node<K, V> root;
    private int size;

    public UncommittedState(CommittedState<K, V> cs) {
        root = cs.root();
        size = cs.size();
    }

    Node<K, V> root() {
        return root;
    }

    int size() {
        return size;
    }

    void setRoot(Node<K, V> newRoot) {
        root = newRoot;
    }

    void increment() {
        size++;
    }

    void decrement() {
        size--;
    }
}

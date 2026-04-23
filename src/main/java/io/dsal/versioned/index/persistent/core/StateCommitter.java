package io.dsal.versioned.index.persistent.core;

public class StateCommitter<K, V> {
    private volatile CommittedState<K, V> cs;

    public StateCommitter() {
        cs = new CommittedState<>();
    }

    public CommittedState<K, V> committed() {
        return cs;
    }

    public void commit(UncommittedState<K, V> us) {
        cs = new CommittedState<>(us.root(), us.size());
    }
}

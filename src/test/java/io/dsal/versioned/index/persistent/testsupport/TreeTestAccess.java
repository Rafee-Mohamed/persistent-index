package io.dsal.versioned.index.persistent.testsupport;

import io.dsal.versioned.index.persistent.PersistentBPlusTree;
import io.dsal.versioned.index.persistent.core.Node;
import io.dsal.versioned.index.persistent.core.StateCommitter;

public final class TreeTestAccess {

    @SuppressWarnings("unchecked")
    public static <K, V> Node<K, V> root(PersistentBPlusTree<K, V> tree) {
        try {
            var f = PersistentBPlusTree.class.getDeclaredField("committer");
            f.setAccessible(true);
            var committer = (StateCommitter<K, V>) f.get(tree);
            return committer.committed().root();
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    public static int maxKeys(PersistentBPlusTree<?, ?> tree) {
        try {
            var f = PersistentBPlusTree.class.getDeclaredField("maxKeys");
            f.setAccessible(true);
            return f.getInt(tree);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    public static int minKeys(PersistentBPlusTree<?, ?> tree) {
        try {
            var f = PersistentBPlusTree.class.getDeclaredField("minKeys");
            f.setAccessible(true);
            return f.getInt(tree);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private TreeTestAccess() {}
}

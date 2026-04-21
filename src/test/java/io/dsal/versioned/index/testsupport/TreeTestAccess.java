package io.dsal.versioned.index.testsupport;

import io.dsal.versioned.index.core.Node;
import io.dsal.versioned.index.core.PersistentBPlusTree;

/**
 * Reads package-private fields from {@link PersistentBPlusTree} via reflection so tests
 * can validate structure without modifying production code.
 */
public final class TreeTestAccess {

    @SuppressWarnings("unchecked")
    public static <K, V> Node<K, V> root(PersistentBPlusTree<K, V> tree) {
        try {
            var f = PersistentBPlusTree.class.getDeclaredField("root");
            f.setAccessible(true);
            return (Node<K, V>) f.get(tree);
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

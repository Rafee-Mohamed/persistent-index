package io.dsal.versioned.index.persistent.testsupport;

import io.dsal.versioned.index.persistent.core.Node;

import java.util.Comparator;

public final class BPlusTreeValidator {

    public static <K, V> void validate(Node<K, V> root, Comparator<K> cmp, int maxKeys, int minKeys) {
        if (root == null) {
            return;
        }
        int leafDepth = uniformLeafDepth(root);
        validateNode(root, cmp, maxKeys, minKeys, true, leafDepth, 0);
    }

    private static <K, V> int uniformLeafDepth(Node<K, V> node) {
        if (node instanceof Node.Internal<K, V> internal) {
            var children = internal.children();
            int d = uniformLeafDepth(children.child(0));
            for (int i = 1; i < children.size(); i++) {
                if (uniformLeafDepth(children.child(i)) != d) {
                    throw new AssertionError("Leaves at different depths under internal node");
                }
            }
            return d + 1;
        }
        return 0;
    }

    private static <K, V> void validateNode(
            Node<K, V> node,
            Comparator<K> cmp,
            int maxKeys,
            int minKeys,
            boolean isRoot,
            int expectedLeafDepth,
            int depth
    ) {
        if (node instanceof Node.Internal<K, V> internal) {
            var keys = internal.keys();
            var children = internal.children();

            if (children.size() != keys.size() + 1) {
                throw new AssertionError("Internal: children.size() " + children.size()
                        + " != keys.size() + 1 == " + (keys.size() + 1));
            }
            if (keys.size() > maxKeys) {
                throw new AssertionError("Internal: too many keys: " + keys.size() + " > " + maxKeys);
            }
            if (!isRoot && keys.size() < minKeys) {
                throw new AssertionError("Internal: too few keys for non-root: " + keys.size() + " < " + minKeys);
            }
            if (isRoot && keys.size() == 0) {
                throw new AssertionError("Root internal node must not be empty");
            }
            for (int i = 0; i < keys.size() - 1; i++) {
                if (keys.compare(i, keys.key(i + 1)) >= 0) {
                    throw new AssertionError("Internal keys not strictly sorted at index " + i);
                }
            }
            for (int i = 0; i < keys.size(); i++) {
                K maxLeft = maxKey(children.child(i));
                if (cmp.compare(maxLeft, keys.key(i)) >= 0) {
                    throw new AssertionError("Max key in left child must be < separator at index " + i);
                }
            }
            int ld = leafDepthBelow(node);
            if (ld != expectedLeafDepth - depth) {
                throw new AssertionError("Unexpected leaf depth below internal node at depth " + depth);
            }
            for (int i = 0; i < children.size(); i++) {
                validateNode(children.child(i), cmp, maxKeys, minKeys, false, expectedLeafDepth, depth + 1);
            }

        } else if (node instanceof Node.Leaf<K, V> leaf) {
            var keys = leaf.keys();
            var vals = leaf.values();

            if (vals.size() != keys.size()) {
                throw new AssertionError("Leaf: values.size() " + vals.size() + " != keys.size() " + keys.size());
            }
            if (keys.size() > maxKeys) {
                throw new AssertionError("Leaf: too many keys: " + keys.size() + " > " + maxKeys);
            }
            if (!isRoot && keys.size() < minKeys) {
                throw new AssertionError("Leaf: too few keys for non-root: " + keys.size() + " < " + minKeys);
            }
            if (isRoot && keys.size() == 0) {
                throw new AssertionError("Root leaf node must not be empty");
            }
            if (depth != expectedLeafDepth) {
                throw new AssertionError("Leaf at wrong depth: expected " + expectedLeafDepth + " got " + depth);
            }
            for (int i = 0; i < keys.size() - 1; i++) {
                if (keys.compare(i, keys.key(i + 1)) >= 0) {
                    throw new AssertionError("Leaf keys not strictly sorted at index " + i);
                }
            }
        }
    }

    private static <K, V> int leafDepthBelow(Node<K, V> node) {
        if (node instanceof Node.Internal<K, V> internal) {
            return leafDepthBelow(internal.children().child(0)) + 1;
        }
        return 0;
    }

    private static <K, V> K maxKey(Node<K, V> node) {
        if (node instanceof Node.Internal<K, V> internal) {
            return maxKey(internal.children().child(internal.children().size() - 1));
        }
        var leaf = (Node.Leaf<K, V>) node;
        return leaf.keys().key(leaf.keys().size() - 1);
    }

    private BPlusTreeValidator() {}
}

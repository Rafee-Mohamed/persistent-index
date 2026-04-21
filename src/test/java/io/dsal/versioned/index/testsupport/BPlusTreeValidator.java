package io.dsal.versioned.index.testsupport;

import io.dsal.versioned.index.core.Node;

import java.util.Comparator;

/**
 * Structural checks for a B+ tree snapshot: shape, ordering, separator semantics, and fill.
 * Prefer calling through {@link TreeStructureAssertions#assertValid} from tests.
 */
public final class BPlusTreeValidator {

    /**
     * Validates {@code root} (possibly {@code null} for an empty tree).
     *
     * @throws AssertionError if any invariant is violated
     */
    public static <K, V> void validate(Node<K, V> root, Comparator<K> cmp, int maxKeys, int minKeys) {
        if (root == null) {
            return;
        }
        int leafDepth = uniformLeafDepth(root);
        validateNode(root, cmp, maxKeys, minKeys, true, leafDepth, 0);
    }

    private static <K, V> int uniformLeafDepth(Node<K, V> node) {
        return switch (node) {
            case Node.Internal<K, V>(var keys, var children) -> {
                int d = uniformLeafDepth(children.child(0));
                for (int i = 1; i < children.size(); i++) {
                    if (uniformLeafDepth(children.child(i)) != d) {
                        throw new AssertionError("Leaves at different depths under internal node");
                    }
                }
                yield d + 1;
            }
            case Node.Leaf<K, V> _ -> 0;
        };
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
        switch (node) {
            case Node.Internal<K, V>(var keys, var children) -> {
                if (children.size() != keys.size() + 1) {
                    throw new AssertionError("Internal: children.size() != keys.size() + 1");
                }
                if (keys.size() > maxKeys) {
                    throw new AssertionError("Internal: too many keys");
                }
                if (!isRoot && keys.size() < minKeys) {
                    throw new AssertionError("Internal: too few keys for non-root");
                }
                if (isRoot && keys.size() == 0) {
                    throw new AssertionError("Root internal must not be empty");
                }
                for (int i = 0; i < keys.size() - 1; i++) {
                    if (keys.compare(i, keys.key(i + 1)) >= 0) {
                        throw new AssertionError("Internal keys not strictly sorted at " + i);
                    }
                }
                for (int i = 0; i < keys.size(); i++) {
                    K maxLeft = maxKey(children.child(i));
                    if (cmp.compare(maxLeft, keys.key(i)) >= 0) {
                        throw new AssertionError("Max in left child must be < separator at " + i);
                    }
                }
                for (int i = 0; i < children.size(); i++) {
                    validateNode(children.child(i), cmp, maxKeys, minKeys, false, expectedLeafDepth, depth + 1);
                }
                int ld = leafDepthBelow(node);
                if (ld != expectedLeafDepth - depth) {
                    throw new AssertionError("Unexpected depth under internal node");
                }
            }
            case Node.Leaf<K, V>(var keys, var vals) -> {
                if (vals.size() != keys.size()) {
                    throw new AssertionError("Leaf: values.size() != keys.size()");
                }
                if (keys.size() > maxKeys) {
                    throw new AssertionError("Leaf: too many keys");
                }
                if (!isRoot && keys.size() < minKeys) {
                    throw new AssertionError("Leaf: too few keys for non-root");
                }
                if (isRoot && keys.size() == 0) {
                    throw new AssertionError("Root leaf must not be empty");
                }
                if (depth != expectedLeafDepth) {
                    throw new AssertionError("Leaf at wrong depth: expected " + expectedLeafDepth + " got " + depth);
                }
                for (int i = 0; i < keys.size() - 1; i++) {
                    if (keys.compare(i, keys.key(i + 1)) >= 0) {
                        throw new AssertionError("Leaf keys not strictly sorted at " + i);
                    }
                }
            }
        }
    }

    private static <K, V> int leafDepthBelow(Node<K, V> node) {
        return switch (node) {
            case Node.Internal<K, V>(var keys, var children) -> leafDepthBelow(children.child(0)) + 1;
            case Node.Leaf<K, V> _ -> 0;
        };
    }

    private static <K, V> K minKey(Node<K, V> node) {
        return switch (node) {
            case Node.Internal<K, V>(var keys, var children) -> minKey(children.child(0));
            case Node.Leaf<K, V>(var keys, var _) -> keys.key(0);
        };
    }

    private static <K, V> K maxKey(Node<K, V> node) {
        return switch (node) {
            case Node.Internal<K, V>(var keys, var children) ->
                    maxKey(children.child(children.size() - 1));
            case Node.Leaf<K, V>(var keys, var _) -> keys.key(keys.size() - 1);
        };
    }

    private BPlusTreeValidator() {}
}

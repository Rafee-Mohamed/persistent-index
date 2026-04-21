package io.dsal.versioned.index.testsupport;

import io.dsal.versioned.index.core.PersistentBPlusTree;

import java.util.Comparator;

/**
 * Single entry point for B+ tree structural invariant checks in tests. Use
 * {@link #assertValid(PersistentBPlusTree, Comparator)} after operations that should
 * leave a well-formed tree; do not duplicate ad-hoc node walks elsewhere.
 */
public final class TreeStructureAssertions {

    /**
     * Asserts that the current snapshot of {@code tree} satisfies structural invariants
     * (internal/leaf shape, key counts, ordering, separator alignment, uniform leaf depth,
     * fill bounds). Empty tree ({@code root == null}) is valid and returns without error.
     *
     * @param tree     tree under test (reads root and fan-out via {@link TreeTestAccess})
     * @param keyOrder total order consistent with {@link io.dsal.versioned.index.layout.KeyStorage}
     *                 for this tree's keys
     */
    public static <K, V> void assertValid(PersistentBPlusTree<K, V> tree, Comparator<K> keyOrder) {
        var root = TreeTestAccess.root(tree);
        if (root == null) {
            return;
        }
        BPlusTreeValidator.validate(
                root,
                keyOrder,
                TreeTestAccess.maxKeys(tree),
                TreeTestAccess.minKeys(tree));
    }

    private TreeStructureAssertions() {}
}

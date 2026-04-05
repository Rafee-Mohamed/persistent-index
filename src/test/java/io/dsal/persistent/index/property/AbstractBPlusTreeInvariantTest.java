package io.dsal.persistent.index.property;

import io.dsal.persistent.index.core.PersistentBPlusTree;
import io.dsal.persistent.index.layout.KeyStorageFactory;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.Arbitrary;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Generic Property-Based Test Suite for B+ Tree invariants.
 *
 * This class validates the core mathematical and logical rules
 * of the PersistentBPlusTree indepedently of the Key Storage layout.
 */
public abstract class AbstractBPlusTreeInvariantTest<K> {

    /** Factory used to instantiate the specific storage layout. */
    protected abstract KeyStorageFactory<K> keyStorageFactory();

    /** Generator telling jqwik how to create individual keys for this storage. */
    @Provide("arbitraryKeys")
    protected abstract Arbitrary<K> arbitraryKeys();

    /** Defines the key relationship used to assert correct order */
    protected abstract java.util.Comparator<K> keyComparator();

    /** Generator telling jqwik how to create lists of keys for bulk operations. */
    @Provide("arbitraryKeyList")
    protected Arbitrary<List<K>> arbitraryKeyList() {
        return arbitraryKeys().list().ofMinSize(0).ofMaxSize(100);
    }

    @Property
    void putAndGetIsIdempotent(@ForAll("arbitraryKeys") K key, @ForAll String value) {
        var tree = new PersistentBPlusTree<K, String>(4, keyStorageFactory());
        
        // Action 1: Put
        tree.put(key, value);
        assertThat(tree.get(key)).isEqualTo(value);

        // Action 2: Put exact same key-value again (Idempotency)
        // Re-putting the exact same value should leave the tree structurally identical and semantically unchanged.
        tree.put(key, value);
        assertThat(tree.get(key)).isEqualTo(value);
    }

    @Property
    void removeReturnsLastValue(@ForAll("arbitraryKeys") K key, @ForAll String value) {
        var tree = new PersistentBPlusTree<K, String>(4, keyStorageFactory());
        
        // Setup state
        tree.put(key, value);
        
        // Property: Remove must return what was stored
        assertThat(tree.remove(key)).isEqualTo(value);
        
        // Property: Calling get afterwards must definitely be null
        assertThat(tree.get(key)).isNull();
    }

    @Property
    void iterationIsStrictlyOrdered(@ForAll("arbitraryKeyList") List<K> keys) {
        var tree = new PersistentBPlusTree<K, String>(4, keyStorageFactory());
        
        // 1. Randomly pump keys into the tree
        for (K key : keys) {
            tree.put(key, "v"); // Dummy value, we are testing key order
        }

        // 2. Iterate and strictly verify order
        var iterator = tree.iterator();
        K prev = null;
        // The tree uses the key order given by the storage factory
        var comparator = keyComparator();

        while (iterator.hasNext()) {
            K current = iterator.next().key();
            if (prev != null) {
                // A B+ tree must never return duplicates during a full scan, 
                // and it must be strictly ascending.
                assertThat(comparator.compare(prev, current))
                        .as("Keys must be strictly ascending: prev=%s, curr=%s", prev, current)
                        .isLessThan(0);
            }
            prev = current;
        }
    }
}

package io.dsal.versioned.index.persistent.property;

import io.dsal.versioned.index.api.OrderedVersionedIndex;
import io.dsal.versioned.index.api.property.AbstractOrderedIndexInvariantTest;
import io.dsal.versioned.index.persistent.PersistentBPlusTree;
import io.dsal.versioned.index.persistent.layout.KeyStorageFactory;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;

import java.util.Comparator;
import java.util.List;

/**
 * Intermediate abstract invariant suite for {@link PersistentBPlusTree} implementations.
 * Concrete subclasses supply only the storage factory, key comparator, and single-key
 * arbitrary; index construction and list derivation are handled here.
 *
 * @param <K> key type under test
 */
public abstract class AbstractBPlusTreeInvariantTest<K> extends AbstractOrderedIndexInvariantTest<K> {

    protected abstract KeyStorageFactory<K> keyStorageFactory();

    protected abstract Comparator<K> keyComparator();

    /** Returns an arbitrary that generates individual keys for this storage layout. */
    protected abstract Arbitrary<K> arbitraryKeys();

    @Override
    protected final Arbitrary<OrderedVersionedIndex<K, String>> indexArbitrary() {
        return Arbitraries.integers().between(2, 8)
                .map(n -> new PersistentBPlusTree<>(n, keyStorageFactory()));
    }

    @Override
    protected final Comparator<K> keyOrder() {
        return keyComparator();
    }

    @Override
    protected final Arbitrary<List<K>> createKeysArbitrary() {
        return arbitraryKeys().list().ofMinSize(0).ofMaxSize(100);
    }
}

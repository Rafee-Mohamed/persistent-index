package io.dsal.versioned.index.persistent.property;

import io.dsal.versioned.index.persistent.PersistentBPlusTree;
import io.dsal.versioned.index.persistent.layout.KeyStorageFactory;
import io.dsal.versioned.index.persistent.testsupport.BPlusTreeValidator;
import io.dsal.versioned.index.persistent.testsupport.IndexTestSupport;
import io.dsal.versioned.index.persistent.testsupport.TreeTestAccess;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Tuple;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;

import java.util.Comparator;
import java.util.List;

class PersistentBPlusTreeByteArrayInvariantTest extends AbstractBPlusTreeInvariantTest<byte[]> {

    @Override
    protected KeyStorageFactory<byte[]> keyStorageFactory() {
        return IndexTestSupport.byteArrayKeyStorageFactory();
    }

    @Override
    protected Comparator<byte[]> keyComparator() {
        return IndexTestSupport.byteArrayComparator();
    }

    @Override
    protected Arbitrary<byte[]> arbitraryKeys() {
        // Biased 60/40 split between short (1–3 bytes) and long (4–8 bytes) keys.
        // Short keys cause real collisions so removes succeed — exercising merges alongside splits.
        // Long keys exercise the variable-length byte serialisation path for larger key sizes.
        return Arbitraries.frequencyOf(
                Tuple.of(6, Arbitraries.bytes().array(byte[].class).ofMinSize(1).ofMaxSize(3)),
                Tuple.of(4, Arbitraries.bytes().array(byte[].class).ofMinSize(4).ofMaxSize(8))
        );
    }

    @Property
    void structureIsValidAfterPutsAndRemoves(
            @ForAll @IntRange(min = 2, max = 8) int maxKeys,
            @ForAll("keys") List<byte[]> keys
    ) {
        var tree = new PersistentBPlusTree<>(maxKeys, IndexTestSupport.byteArrayKeyStorageFactory());
        for (var k : keys) tree.put(k, "v");
        for (int i = 0; i < keys.size() / 2; i++) tree.remove(keys.get(i));

        BPlusTreeValidator.validate(
                TreeTestAccess.root(tree),
                IndexTestSupport.byteArrayComparator(),
                TreeTestAccess.maxKeys(tree),
                TreeTestAccess.minKeys(tree));
    }
}

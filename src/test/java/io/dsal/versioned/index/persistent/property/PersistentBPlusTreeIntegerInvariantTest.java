package io.dsal.versioned.index.persistent.property;

import io.dsal.versioned.index.persistent.PersistentBPlusTree;
import io.dsal.versioned.index.persistent.layout.KeyStorageFactory;
import io.dsal.versioned.index.persistent.testsupport.BPlusTreeValidator;
import io.dsal.versioned.index.persistent.testsupport.IndexTestSupport;
import io.dsal.versioned.index.persistent.testsupport.TreeTestAccess;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;

import java.util.Comparator;
import java.util.List;

class PersistentBPlusTreeIntegerInvariantTest extends AbstractBPlusTreeInvariantTest<Integer> {

    @Override
    protected KeyStorageFactory<Integer> keyStorageFactory() {
        return IndexTestSupport.integerKeyStorageFactory();
    }

    @Override
    protected Comparator<Integer> keyComparator() {
        return IndexTestSupport.INTEGER_COMPARATOR;
    }

    @Override
    protected Arbitrary<Integer> arbitraryKeys() {
        return Arbitraries.integers();
    }

    @Property
    void structureIsValidAfterPutsAndRemoves(
            @ForAll @IntRange(min = 2, max = 8) int maxKeys,
            @ForAll("keys") List<Integer> keys
    ) {
        var tree = new PersistentBPlusTree<>(maxKeys, IndexTestSupport.integerKeyStorageFactory());
        for (var k : keys) tree.put(k, "v" + k);
        for (int i = 0; i < keys.size() / 2; i++) tree.remove(keys.get(i));

        BPlusTreeValidator.validate(
                TreeTestAccess.root(tree),
                IndexTestSupport.INTEGER_COMPARATOR,
                TreeTestAccess.maxKeys(tree),
                TreeTestAccess.minKeys(tree));
    }
}

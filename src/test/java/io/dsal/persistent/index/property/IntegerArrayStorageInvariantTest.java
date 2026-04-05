package io.dsal.persistent.index.property;

import io.dsal.persistent.index.layout.KeyStorageFactory;
import io.dsal.persistent.index.testsupport.TestKeyFixtures;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;

public class IntegerArrayStorageInvariantTest extends AbstractBPlusTreeInvariantTest<Integer> {

    @Override
    protected KeyStorageFactory<Integer> keyStorageFactory() {
        return TestKeyFixtures.integerArrayKeyStorageFactory();
    }

    @Override
    protected java.util.Comparator<Integer> keyComparator() {
        return java.util.Comparator.naturalOrder();
    }

    @Override
    protected Arbitrary<Integer> arbitraryKeys() {
        // Generates integers strictly between limits if you want bounds, 
        // but default arbitrary integers hits the best known edge-cases (0, -1, MAX, MIN).
        return Arbitraries.integers();
    }
}

package io.dsal.persistent.index.property.state;

import io.dsal.persistent.index.layout.KeyStorageFactory;
import io.dsal.persistent.index.testsupport.TestKeyFixtures;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;

import java.util.Comparator;

public class IntegerArrayStatefulTest extends AbstractBPlusTreeStatefulTest<Integer> {

    @Override
    protected KeyStorageFactory<Integer> keyStorageFactory() {
        return TestKeyFixtures.integerArrayKeyStorageFactory();
    }

    @Override
    protected Comparator<Integer> keyComparator() {
        return Comparator.naturalOrder();
    }

    @Override
    protected Arbitrary<Integer> arbitraryKeys() {
        // Tightly bounding to [-1500, 1500] restricts the keyspace to 3000 total variables.
        // Over a trace of 500 operations, this mathematical bottleneck guarantees high collision rates.
        // Puts will frequently overwrite keys, and Removes will frequently strike live data,
        // mirroring real-world aggressive database compaction behaviours.
        return Arbitraries.integers().between(-1500, 1500);
    }

    @Override
    protected java.util.function.Function<Integer, String> keyStringifier() {
        return String::valueOf;
    }
}

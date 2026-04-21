package io.dsal.versioned.index.property.state;

import io.dsal.versioned.index.layout.KeyStorageFactory;
import io.dsal.versioned.index.testsupport.TestKeyFixtures;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;

import java.util.Comparator;

public class PackedByteStatefulTest extends AbstractBPlusTreeStatefulTest<byte[]> {

    @Override
    protected KeyStorageFactory<byte[]> keyStorageFactory() {
        return TestKeyFixtures.lexicographicByteKeyStorageFactory();
    }

    @Override
    protected Comparator<byte[]> keyComparator() {
        return TestKeyFixtures.byteArrayLexicographicOrder();
    }

    @Override
    protected Arbitrary<byte[]> arbitraryKeys() {
        return Arbitraries.bytes()
                .array(byte[].class)
                // larger bytes sequence say 4 results in 256^4 unique keys (equal to integer range ~2.17 billion.
                // If tried 1000 tries for generating values within the range with collision
                // is mathematically zero (1 / 256^4). If keys never overlap, the tree only grows linearly.
                // Put will never overwrite. Remove will rarely succeed. But, Stateful engine will be completely blind to
                // compaction logic for PackedByte trees as we need arbitrary length of bytes to ensure the working of
                // variable length bytes therefore keeping it as 8 but collision is near zero and map
                // grows linearly with put.
                .ofMaxSize(8)
                .filter(bytes -> bytes.length > 0);
    }

    @Override
    protected java.util.function.Function<byte[], String> keyStringifier() {
        return java.util.Arrays::toString;
    }
}

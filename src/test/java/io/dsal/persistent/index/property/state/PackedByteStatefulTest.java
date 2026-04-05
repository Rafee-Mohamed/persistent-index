package io.dsal.persistent.index.property.state;

import io.dsal.persistent.index.layout.KeyStorageFactory;
import io.dsal.persistent.index.testsupport.TestKeyFixtures;
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
                .ofMaxSize(64)
                .filter(bytes -> bytes.length > 0);
    }

    @Override
    protected java.util.function.Function<byte[], String> keyStringifier() {
        return java.util.Arrays::toString;
    }
}

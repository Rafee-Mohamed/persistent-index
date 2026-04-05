package io.dsal.persistent.index.property;

import io.dsal.persistent.index.layout.KeyStorageFactory;
import io.dsal.persistent.index.testsupport.TestKeyFixtures;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;

public class PackedByteStorageInvariantTest extends AbstractBPlusTreeInvariantTest<byte[]> {

    @Override
    protected KeyStorageFactory<byte[]> keyStorageFactory() {
        return TestKeyFixtures.lexicographicByteKeyStorageFactory();
    }

    @Override
    protected java.util.Comparator<byte[]> keyComparator() {
        return TestKeyFixtures.byteArrayLexicographicOrder();
    }

    @Override
    protected Arbitrary<byte[]> arbitraryKeys() {
        // PackedByte implementation may be strictly designed for non-empty keys
        // (Length > 0 rules generally apply for byte representations of DB keys)
        return Arbitraries.bytes()
                .array(byte[].class)
                .ofMaxSize(64)
                .filter(bytes -> bytes.length > 0);
    }
}

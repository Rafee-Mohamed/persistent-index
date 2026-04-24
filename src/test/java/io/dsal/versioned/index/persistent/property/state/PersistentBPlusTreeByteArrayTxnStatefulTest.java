package io.dsal.versioned.index.persistent.property.state;

import io.dsal.versioned.index.api.property.state.TreeOracleState;
import io.dsal.versioned.index.persistent.layout.KeyStorageFactory;
import io.dsal.versioned.index.persistent.testsupport.IndexTestSupport;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Tuple;

import java.util.Arrays;
import java.util.Comparator;
import java.util.function.Function;

class PersistentBPlusTreeByteArrayTxnStatefulTest extends AbstractBPlusTreeTxnStatefulTest<byte[]> {

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
        // Short keys (say length 1 = 256 unique, length 2 = 65536 unique) cause real collisions so puts
        // overwrite and removes succeed — exercising merges and compaction logic.
        // Long keys (say length 4 = 256^4 ~2.17 billion, length 8 = 256^8 ~18.4 quintillion) have near-zero
        // collision and exercise the variable-length byte serialisation path for larger key sizes.
        // Together this covers both split and merge paths while keeping variable-length storage exercised.
        return Arbitraries.frequencyOf(
                Tuple.of(6, Arbitraries.bytes().array(byte[].class).ofMinSize(1).ofMaxSize(3)),
                Tuple.of(4, Arbitraries.bytes().array(byte[].class).ofMinSize(4).ofMaxSize(8))
        );
    }

    @Override
    protected Arbitrary<String> arbitraryVals() {
        return Arbitraries.strings().alpha().ofMaxLength(10);
    }

    @Override
    protected Function<byte[], String> keyStringifier() {
        return Arrays::toString;
    }

    @Override
    protected TreeOracleState.EntryEquality<byte[], String> entryEquality() {
        return TreeOracleState.comparatorEquality(IndexTestSupport.byteArrayComparator());
    }
}

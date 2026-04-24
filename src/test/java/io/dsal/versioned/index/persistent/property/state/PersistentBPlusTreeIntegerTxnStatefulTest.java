package io.dsal.versioned.index.persistent.property.state;

import io.dsal.versioned.index.api.property.state.TreeOracleState;
import io.dsal.versioned.index.persistent.layout.KeyStorageFactory;
import io.dsal.versioned.index.persistent.testsupport.IndexTestSupport;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;

import java.util.Comparator;
import java.util.function.Function;

class PersistentBPlusTreeIntegerTxnStatefulTest extends AbstractBPlusTreeTxnStatefulTest<Integer> {

    @Override
    protected KeyStorageFactory<Integer> keyStorageFactory() {
        return IndexTestSupport.integerKeyStorageFactory();
    }

    @Override
    protected Comparator<Integer> keyComparator() {
        return IndexTestSupport.INTEGER_COMPARATOR;
    }

    private static final int KEY_BOUND = 1500;

    @Override
    protected Arbitrary<Integer> arbitraryKeys() {
        return Arbitraries.integers().between(-KEY_BOUND, KEY_BOUND);
    }

    @Override
    protected Arbitrary<String> arbitraryVals() {
        return Arbitraries.strings().alpha().ofMaxLength(10);
    }

    @Override
    protected Function<Integer, String> keyStringifier() {
        return Object::toString;
    }

    @Override
    protected TreeOracleState.EntryEquality<Integer, String> entryEquality() {
        return TreeOracleState.standardEquality();
    }
}

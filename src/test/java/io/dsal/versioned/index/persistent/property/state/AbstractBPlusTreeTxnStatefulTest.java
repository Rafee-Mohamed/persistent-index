package io.dsal.versioned.index.persistent.property.state;

import io.dsal.versioned.index.api.property.state.IterateAction;
import io.dsal.versioned.index.api.property.state.PutAction;
import io.dsal.versioned.index.api.property.state.RangeAction;
import io.dsal.versioned.index.api.property.state.RemoveAction;
import io.dsal.versioned.index.api.property.state.TreeOracleState;
import io.dsal.versioned.index.api.property.state.TxnMultiOpAction;
import io.dsal.versioned.index.persistent.PersistentBPlusTree;
import io.dsal.versioned.index.persistent.layout.KeyStorageFactory;
import io.dsal.versioned.index.persistent.testsupport.TreeMapOracle;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.state.ActionChain;

import java.util.Comparator;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Abstract stateful property-based test for {@link PersistentBPlusTree} that exercises
 * multi-operation transactions (via {@link TxnMultiOpAction}) alongside the standard
 * single-op mutations, reads, and range queries.
 *
 * <p>Runs 1000 jqwik tries across {@code maxKeys} values 2 to 10.
 *
 * @param <K> key type
 */
public abstract class AbstractBPlusTreeTxnStatefulTest<K> {

    protected abstract KeyStorageFactory<K> keyStorageFactory();

    protected abstract Comparator<K> keyComparator();

    protected abstract Arbitrary<K> arbitraryKeys();

    protected abstract Arbitrary<String> arbitraryVals();

    protected abstract Function<K, String> keyStringifier();

    protected abstract TreeOracleState.EntryEquality<K, String> entryEquality();

    protected int minOpsPerTxn() { return 5; }

    protected int maxOpsPerTxn() { return 50; }

    private Arbitrary<Supplier<TreeOracleState<K, String>>> stateArbitrary() {
        return Arbitraries.integers().between(2, 10)
                .map(n -> () -> new TreeOracleState<>(
                        new PersistentBPlusTree<>(n, keyStorageFactory()),
                        new TreeMapOracle<>(keyComparator()),
                        entryEquality()
                ));
    }

    @Provide
    final Arbitrary<ActionChain<TreeOracleState<K, String>>> actionChain() {
        return stateArbitrary().flatMap(supplier ->
                ActionChain.startWith(supplier)
                        .withAction(5, new PutAction<>(arbitraryKeys(), arbitraryVals(), keyStringifier()))
                        .withAction(2, new RemoveAction<>(arbitraryKeys(), keyStringifier()))
                        .withAction(2, new IterateAction<>())
                        .withAction(2, new RangeAction<>(arbitraryKeys(), keyComparator(), keyStringifier()))
                        .withAction(3, new TxnMultiOpAction<>(arbitraryKeys(), arbitraryVals(), keyStringifier(), minOpsPerTxn(), maxOpsPerTxn()))
                        .withMaxTransformations(500)
        );
    }

    @Property(tries = 1000)
    void txnStatefulStressTest(@ForAll("actionChain") ActionChain<TreeOracleState<K, String>> chain) {
        chain.run();
    }
}

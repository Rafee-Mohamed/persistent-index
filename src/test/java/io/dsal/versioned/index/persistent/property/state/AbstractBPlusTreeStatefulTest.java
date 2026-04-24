package io.dsal.versioned.index.persistent.property.state;

import io.dsal.versioned.index.api.property.state.AbstractOrderedIndexStatefulTest;
import io.dsal.versioned.index.api.property.state.ContainsAction;
import io.dsal.versioned.index.api.property.state.GetAction;
import io.dsal.versioned.index.api.property.state.IterateAction;
import io.dsal.versioned.index.api.property.state.PutAction;
import io.dsal.versioned.index.api.property.state.RangeAction;
import io.dsal.versioned.index.api.property.state.RemoveAction;
import io.dsal.versioned.index.api.property.state.TreeOracleState;
import io.dsal.versioned.index.persistent.PersistentBPlusTree;
import io.dsal.versioned.index.persistent.layout.KeyStorageFactory;
import io.dsal.versioned.index.persistent.testsupport.TreeMapOracle;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.state.ActionChain;

import java.util.Comparator;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Intermediate abstract stateful suite for {@link PersistentBPlusTree} implementations.
 * Concrete subclasses supply the storage factory, comparator, key arbitrary, stringifier,
 * and entry equality; all action construction and state wiring are handled here.
 *
 * <p>Runs 1000 jqwik tries (vs. the api-level 20) to exercise deep compaction cascades
 * across the full range of {@code maxKeys} values from 2 to 10.
 *
 * @param <K> key type under test
 */
public abstract class AbstractBPlusTreeStatefulTest<K> extends AbstractOrderedIndexStatefulTest<K, String> {

    protected abstract KeyStorageFactory<K> keyStorageFactory();

    protected abstract Comparator<K> keyComparator();

    protected abstract Arbitrary<K> arbitraryKeys();

    protected abstract Function<K, String> keyStringifier();

    protected abstract TreeOracleState.EntryEquality<K, String> entryEquality();

    @Override
    protected Arbitrary<Supplier<TreeOracleState<K, String>>> stateArbitrary() {
        return Arbitraries.integers().between(2, 10)
                .map(n -> () -> new TreeOracleState<>(
                        new PersistentBPlusTree<>(n, keyStorageFactory()),
                        new TreeMapOracle<>(keyComparator()),
                        entryEquality()
                ));
    }

    @Override
    protected PutAction<K, String> putAction() {
        return new PutAction<>(
                arbitraryKeys(),
                Arbitraries.strings().alpha().ofMaxLength(10),
                keyStringifier()
        );
    }

    @Override
    protected RemoveAction<K, String> removeAction() {
        return new RemoveAction<>(arbitraryKeys(), keyStringifier());
    }

    @Override
    protected GetAction<K, String> getAction() {
        return new GetAction<>(arbitraryKeys(), keyStringifier());
    }

    @Override
    protected ContainsAction<K, String> containsAction() {
        return new ContainsAction<>(arbitraryKeys(), keyStringifier());
    }

    @Override
    protected RangeAction<K, String> rangeAction() {
        return new RangeAction<>(arbitraryKeys(), keyComparator(), keyStringifier());
    }

    @Override
    protected IterateAction<K, String> iterateAction() {
        return new IterateAction<>();
    }

    @Override
    @Property(tries = 1000)
    protected void statefulStressTest(@ForAll("actionChain") ActionChain<TreeOracleState<K, String>> chain) {
        chain.run();
    }
}

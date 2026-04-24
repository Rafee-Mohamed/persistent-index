package io.dsal.versioned.index.api.property.state;

import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.state.ActionChain;

import java.util.function.Supplier;

/**
 * Abstract stateful property-based test for any {@link io.dsal.versioned.index.api.OrderedVersionedIndex}
 * implementation. Concrete subclasses supply the state factory and per-type action instances.
 * The action chain runs up to 1000 transformations per trial, exercising all read and write APIs
 * (put, remove, get, contains, range × 4 types × 2 directions, full scan) against a
 * {@link io.dsal.versioned.index.persistent.testsupport.TreeMapOracle oracle}.
 *
 * @param <K> key type
 * @param <V> value type
 */
public abstract class AbstractOrderedIndexStatefulTest<K, V> {

    protected abstract Arbitrary<Supplier<TreeOracleState<K, V>>> stateArbitrary();

    protected abstract PutAction<K, V> putAction();

    protected abstract RemoveAction<K, V> removeAction();

    protected abstract GetAction<K, V> getAction();

    protected abstract ContainsAction<K, V> containsAction();

    protected abstract RangeAction<K, V> rangeAction();

    protected abstract IterateAction<K, V> iterateAction();

    @Provide
    final Arbitrary<ActionChain<TreeOracleState<K, V>>> actionChain() {
        return stateArbitrary().flatMap(supplier ->
                ActionChain.startWith(supplier)
                        .withAction(5, putAction())
                        .withAction(2, removeAction())
                        .withAction(2, getAction())
                        .withAction(1, containsAction())
                        .withAction(1, rangeAction())
                        .withAction(1, iterateAction())
                        .withMaxTransformations(1000)
        );
    }

    @Property(tries = 20)
    protected void statefulStressTest(@ForAll("actionChain") ActionChain<TreeOracleState<K, V>> chain) {
        chain.run();
    }
}

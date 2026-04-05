package io.dsal.persistent.index.property.state;

import io.dsal.persistent.index.layout.KeyStorageFactory;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.state.ActionChain;
import java.util.function.Function;

import java.util.Comparator;

public abstract class AbstractBPlusTreeStatefulTest<K> {

    protected abstract KeyStorageFactory<K> keyStorageFactory();
    
    protected abstract Comparator<K> keyComparator();

    protected abstract Arbitrary<K> arbitraryKeys();

    protected abstract Function<K, String> keyStringifier();

    @Provide("actionChain")
    Arbitrary<ActionChain<TreeOracleState<K>>> actionChain() {
        Arbitrary<K> keys = arbitraryKeys();
        Function<K, String> str = keyStringifier();
        
        // We dynamically inject random maxKeys between 2 and 10 using flatMap.
        // During Jqwik's 1000 run cycle, this creates 1000 isolated B-Trees of varying geometries.
        // A low maxKeys parameter (e.g. 2 or 3) forces rapid page splits, creating extremely deep 
        // layered trees (depth 8+) which aggressively test sibling merges during Removes.
        Arbitrary<Integer> maxKeys = Arbitraries.integers().between(2, 10);
        
        return maxKeys.flatMap(mk -> ActionChain.startWith(
                () -> new TreeOracleState<>(mk, keyStorageFactory(), keyComparator()))
            .withAction(new PutAction<>(keys, str))
            .withAction(new GetAction<>(keys, str))
            .withAction(new RemoveAction<>(keys, str))
            .withAction(new RangeAction<>(keys, str))
            .withAction(new IterateAction<>())
            // 1000 actions peak tree size at ~150-300 elements because Puts trigger ~20% 
            // of the time and frequently collide/overwrite. This ensures complex fragmentation 
            // cascades without locking up the JVM CPU or exceeding Shrinker time limits.
            .withMaxTransformations(1000)
        );
    }

    // Jqwik defaults to 1000 tries per @Property.
    @Property(tries = 1000)
    void statefulStressTest(@ForAll("actionChain") ActionChain<TreeOracleState<K>> chain) {
        chain.run();
    }
}

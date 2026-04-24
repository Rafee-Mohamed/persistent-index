package io.dsal.versioned.index.api.property.state;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.state.Action;
import net.jqwik.api.state.Transformer;

public final class IterateAction<K, V> implements Action.Independent<TreeOracleState<K, V>> {

    @Override
    public Arbitrary<Transformer<TreeOracleState<K, V>>> transformer() {
        return Arbitraries.just(
                Transformer.mutate("iterate", state -> state.assertFullScanMatchesOracle())
        );
    }
}

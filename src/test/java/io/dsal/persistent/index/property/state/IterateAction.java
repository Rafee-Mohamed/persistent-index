package io.dsal.persistent.index.property.state;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.state.Action;
import net.jqwik.api.state.Transformer;

public class IterateAction<K> implements Action.Independent<TreeOracleState<K>> {

    public IterateAction() {}

    @Override
    public boolean precondition(TreeOracleState<K> state) {
        return true;
    }

    @Override
    public Arbitrary<Transformer<TreeOracleState<K>>> transformer() {
        return Arbitraries.just(
                Transformer.mutate("IterateAndVerify", TreeOracleState::iterateAndVerify)
        );
    }
}

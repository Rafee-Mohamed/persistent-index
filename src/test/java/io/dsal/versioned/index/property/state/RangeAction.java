package io.dsal.versioned.index.property.state;

import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.state.Action;
import net.jqwik.api.state.Transformer;
import java.util.function.Function;

public class RangeAction<K> implements Action.Independent<TreeOracleState<K>> {
    private final Arbitrary<K> keys;
    private final Function<K, String> stringifier;

    public RangeAction(Arbitrary<K> keys, Function<K, String> stringifier) {
        this.keys = keys;
        this.stringifier = stringifier;
    }

    @Override
    public boolean precondition(TreeOracleState<K> state) {
        return true;
    }

    @Override
    public Arbitrary<Transformer<TreeOracleState<K>>> transformer() {
        return Combinators.combine(keys, keys).as((from, to) ->
                Transformer.mutate("Range(" + stringifier.apply(from) + ", " + stringifier.apply(to) + ")",
                        state -> state.range(from, to))
        );
    }
}

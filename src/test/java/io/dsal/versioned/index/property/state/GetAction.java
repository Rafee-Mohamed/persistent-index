package io.dsal.versioned.index.property.state;

import net.jqwik.api.Arbitrary;
import net.jqwik.api.state.Action;
import net.jqwik.api.state.Transformer;
import java.util.function.Function;

public class GetAction<K> implements Action.Independent<TreeOracleState<K>> {
    private final Arbitrary<K> keys;
    private final Function<K, String> stringifier;

    public GetAction(Arbitrary<K> keys, Function<K, String> stringifier) {
        this.keys = keys;
        this.stringifier = stringifier;
    }

    @Override
    public boolean precondition(TreeOracleState<K> state) {
        return true;
    }

    @Override
    public Arbitrary<Transformer<TreeOracleState<K>>> transformer() {
        return keys.map(k ->
                Transformer.mutate("Get(" + stringifier.apply(k) + ")", state -> state.get(k))
        );
    }
}

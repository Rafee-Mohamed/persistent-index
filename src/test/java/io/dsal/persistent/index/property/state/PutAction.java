package io.dsal.persistent.index.property.state;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.state.Action;
import net.jqwik.api.state.Transformer;
import java.util.function.Function;

public class PutAction<K> implements Action.Independent<TreeOracleState<K>> {
    private final Arbitrary<K> keys;
    private final Function<K, String> stringifier;
    private final Arbitrary<String> values = Arbitraries.strings().ofMinLength(0).ofMaxLength(10);

    public PutAction(Arbitrary<K> keys, Function<K, String> stringifier) {
        this.keys = keys;
        this.stringifier = stringifier;
    }

    @Override
    public boolean precondition(TreeOracleState<K> state) {
        return true;
    }

    @Override
    public Arbitrary<Transformer<TreeOracleState<K>>> transformer() {
        return Combinators.combine(keys, values).as((k, v) ->
                Transformer.mutate("Put(" + stringifier.apply(k) + ", " + v + ")", state -> state.put(k, v))
        );
    }
}

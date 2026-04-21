package io.dsal.versioned.index.property.state;

import net.jqwik.api.Arbitrary;
import net.jqwik.api.state.Action;
import net.jqwik.api.state.Transformer;
import java.util.function.Function;

public class RemoveAction<K> implements Action.Independent<TreeOracleState<K>> {
    private final Arbitrary<K> keys;
    private final Function<K, String> stringifier;

    public RemoveAction(Arbitrary<K> keys, Function<K, String> stringifier) {
        this.keys = keys;
        this.stringifier = stringifier;
    }

    @Override
    public boolean precondition(TreeOracleState<K> state) {
        return !state.isEmpty();
    }

    @Override
    public Arbitrary<Transformer<TreeOracleState<K>>> transformer() {
        return keys.map(k ->
                Transformer.mutate("Remove(" + stringifier.apply(k) + ")", state -> state.remove(k))
        );
    }
}

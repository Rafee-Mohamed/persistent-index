package io.dsal.versioned.index.api.property.state;

import net.jqwik.api.Arbitrary;
import net.jqwik.api.state.Action;
import net.jqwik.api.state.Transformer;

import java.util.function.Function;

public final class ContainsAction<K, V> implements Action.Independent<TreeOracleState<K, V>> {

    private final Arbitrary<K> keys;
    private final Function<K, String> keyStringifier;

    public ContainsAction(Arbitrary<K> keys) {
        this(keys, Object::toString);
    }

    public ContainsAction(Arbitrary<K> keys, Function<K, String> keyStringifier) {
        this.keys = keys;
        this.keyStringifier = keyStringifier;
    }

    @Override
    public Arbitrary<Transformer<TreeOracleState<K, V>>> transformer() {
        return keys.map(k ->
                Transformer.mutate("contains(" + keyStringifier.apply(k) + ")", state ->
                        state.assertContainsMatchesOracle(k)
                )
        );
    }
}

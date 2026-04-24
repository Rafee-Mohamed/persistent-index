package io.dsal.versioned.index.api.property.state;

import net.jqwik.api.Arbitrary;
import net.jqwik.api.state.Action;
import net.jqwik.api.state.Transformer;

import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

public final class GetAction<K, V> implements Action.Independent<TreeOracleState<K, V>> {

    private final Arbitrary<K> keys;
    private final Function<K, String> keyStringifier;

    public GetAction(Arbitrary<K> keys) {
        this(keys, Object::toString);
    }

    public GetAction(Arbitrary<K> keys, Function<K, String> keyStringifier) {
        this.keys = keys;
        this.keyStringifier = keyStringifier;
    }

    @Override
    public Arbitrary<Transformer<TreeOracleState<K, V>>> transformer() {
        return keys.map(k ->
                Transformer.mutate("get(" + keyStringifier.apply(k) + ")", state -> {
                    assertThat(state.index.get(k)).isEqualTo(state.oracle.get(k));
                })
        );
    }
}

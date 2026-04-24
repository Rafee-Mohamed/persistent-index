package io.dsal.versioned.index.api.property.state;

import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.state.Action;
import net.jqwik.api.state.Transformer;

import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

public final class PutAction<K, V> implements Action.Independent<TreeOracleState<K, V>> {

    private final Arbitrary<K> keys;
    private final Arbitrary<V> vals;
    private final Function<K, String> keyStringifier;

    public PutAction(Arbitrary<K> keys, Arbitrary<V> vals) {
        this(keys, vals, Object::toString);
    }

    public PutAction(Arbitrary<K> keys, Arbitrary<V> vals, Function<K, String> keyStringifier) {
        this.keys = keys;
        this.vals = vals;
        this.keyStringifier = keyStringifier;
    }

    @Override
    public Arbitrary<Transformer<TreeOracleState<K, V>>> transformer() {
        return Combinators.combine(keys, vals).as((k, v) ->
                Transformer.mutate("put(" + keyStringifier.apply(k) + ", " + v + ")", state -> {
                    assertThat(state.index.put(k, v)).isEqualTo(state.oracle.put(k, v));
                })
        );
    }
}

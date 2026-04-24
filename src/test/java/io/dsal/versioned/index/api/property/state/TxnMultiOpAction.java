package io.dsal.versioned.index.api.property.state;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.state.Action;
import net.jqwik.api.state.Transformer;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Stateful action that executes a multi-operation transaction in a single atomic commit.
 *
 * <p>Generates {@code minOps}–{@code maxOps} operations — each either a PUT(key, value) or
 * REMOVE(key) — applies them all inside a single transaction, then commits. Post-commit,
 * verifies that the index agrees with the oracle via
 * {@link TreeOracleState#assertFullScanMatchesOracle()}.
 *
 * <p>Higher {@code maxOps} values exercise deeper cascading splits and merges (especially at
 * {@code maxKeys=2}), at the cost of longer per-action runtime.
 *
 * @param <K> key type
 * @param <V> value type
 */
public final class TxnMultiOpAction<K, V> implements Action.Independent<TreeOracleState<K, V>> {

    private final Arbitrary<K> keys;
    private final Arbitrary<V> vals;
    private final Function<K, String> keyStringifier;
    private final int minOps;
    private final int maxOps;

    public TxnMultiOpAction(Arbitrary<K> keys, Arbitrary<V> vals) {
        this(keys, vals, Object::toString);
    }

    public TxnMultiOpAction(Arbitrary<K> keys, Arbitrary<V> vals, Function<K, String> keyStringifier) {
        this(keys, vals, keyStringifier, 5, 50);
    }

    public TxnMultiOpAction(Arbitrary<K> keys, Arbitrary<V> vals, Function<K, String> keyStringifier,
                            int minOps, int maxOps) {
        this.keys = keys;
        this.vals = vals;
        this.keyStringifier = keyStringifier;
        this.minOps = minOps;
        this.maxOps = maxOps;
    }

    private record Op<K, V>(boolean isPut, K key, V val) {}

    @Override
    public Arbitrary<Transformer<TreeOracleState<K, V>>> transformer() {
        return Arbitraries.integers().between(minOps, maxOps).flatMap(count -> {
            var opArb = Arbitraries.integers().between(0, 1).flatMap(isPutInt ->
                    keys.flatMap(k -> vals.map(v -> new Op<K, V>(isPutInt == 1, k, v)))
            );
            return opArb.list().ofSize(count).map(ops -> {
                String label = buildLabel(ops);
                return Transformer.mutate(label, state -> {
                    var txn = state.index.txn();

                    for (var op : ops) {
                        if (op.isPut()) {
                            txn.put(op.key(), op.val());
                        } else {
                            txn.remove(op.key());
                        }
                    }

                    txn.commit();

                    // Apply same ops to the oracle and verify post-commit correctness
                    for (var op : ops) {
                        if (op.isPut()) {
                            state.oracle.put(op.key(), op.val());
                        } else {
                            state.oracle.remove(op.key());
                        }
                    }
                    state.assertFullScanMatchesOracle();
                });
            });
        });
    }

    private String buildLabel(List<Op<K, V>> ops) {
        var puts = new ArrayList<String>();
        var removes = new ArrayList<String>();
        for (var op : ops) {
            if (op.isPut()) puts.add(keyStringifier.apply(op.key()));
            else removes.add(keyStringifier.apply(op.key()));
        }
        return "txn[puts=" + puts.size() + ",removes=" + removes.size() + "]";
    }
}

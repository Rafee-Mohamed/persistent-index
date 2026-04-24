package io.dsal.versioned.index.api.property.state;

import io.dsal.versioned.index.api.Range;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.state.Action;
import net.jqwik.api.state.Transformer;

import java.util.Comparator;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Exercises all four {@link Range} types (ASC and DESC) against the oracle.
 *
 * <p>Two construction modes:
 * <ul>
 *   <li>Ordinal-based: caller provides integer ordinals + a factory that maps {@code (lo,hi)} to
 *       key ranges. Useful when key types map cleanly to integers (e.g. {@code Integer} keys).</li>
 *   <li>Key-based: caller provides an arbitrary over raw keys + a comparator to determine order.
 *       Two keys are picked, sorted by the comparator, and all four range types are exercised.
 *       Use when key generation does not map through integer ordinals (e.g. variable-length
 *       {@code byte[]} keys).</li>
 * </ul>
 */
public final class RangeAction<K, V> implements Action.Independent<TreeOracleState<K, V>> {

    private final Arbitrary<Transformer<TreeOracleState<K, V>>> transformer;

    @SuppressWarnings("unchecked")
    public RangeAction(Arbitrary<Integer> ordinals, BiFunction<Integer, Integer, Range<K>[]> rangeFactory) {
        this.transformer = Combinators.combine(ordinals, ordinals).as((a, b) -> {
            int lo = Math.min(a, b);
            int hi = Math.max(a, b);
            Range<K>[] ranges = rangeFactory.apply(lo, hi);
            return Transformer.mutate("range[" + lo + ".." + hi + "]", state -> {
                for (Range<K> r : ranges) {
                    state.assertRangeMatchesOracle(r);
                }
            });
        });
    }

    @SuppressWarnings("unchecked")
    public RangeAction(Arbitrary<K> keys, Comparator<K> keyCmp, Function<K, String> keyStringifier) {
        this.transformer = Combinators.combine(keys, keys).as((a, b) -> {
            K lo = keyCmp.compare(a, b) <= 0 ? a : b;
            K hi = keyCmp.compare(a, b) <= 0 ? b : a;
            Range<K>[] ranges = new Range[]{
                    Range.closed(lo, hi),
                    Range.open(lo, hi),
                    Range.closedOpen(lo, hi),
                    Range.openClosed(lo, hi)
            };
            String label = "range[" + keyStringifier.apply(lo) + ".." + keyStringifier.apply(hi) + "]";
            return Transformer.mutate(label, state -> {
                for (Range<K> r : ranges) {
                    state.assertRangeMatchesOracle(r);
                }
            });
        });
    }

    @Override
    public Arbitrary<Transformer<TreeOracleState<K, V>>> transformer() {
        return transformer;
    }
}

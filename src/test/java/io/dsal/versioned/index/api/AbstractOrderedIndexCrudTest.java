package io.dsal.versioned.index.api;

import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

/**
 * Abstract CRUD scenario suite for any {@link OrderedVersionedIndex} implementation.
 * Concrete subclasses supply index factories; each scenario receives a fresh index.
 * All scenarios test exclusively via the {@link OrderedVersionedIndex} interface —
 * no implementation details are referenced.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class AbstractOrderedIndexCrudTest {

    protected abstract Stream<Supplier<OrderedVersionedIndex<Integer, String>>> indexFactories();

    private static List<Map.Entry<Integer, String>> rangeAsc(
            OrderedVersionedIndex<Integer, String> index, int from, int to) {
        var result = new ArrayList<Map.Entry<Integer, String>>();
        index.forEach(Direction.ASC, Range.closed(from, to), (k, v) -> result.add(Map.entry(k, v)));
        return result;
    }

    record CaseSpec(String name, Consumer<OrderedVersionedIndex<Integer, String>> steps) {
        @Override
        public String toString() { return name; }
    }

    private static Stream<CaseSpec> namedCases() {
        return Stream.of(
                new CaseSpec(
                        "emptyIndex_getOnMissingKeyReturnsEmpty",
                        idx -> assertThat(idx.get(7)).isEmpty()
                ),
                new CaseSpec(
                        "emptyIndex_removeOnMissingKeyReturnsEmpty",
                        idx -> assertThat(idx.remove(7)).isEmpty()
                ),
                new CaseSpec(
                        "emptyIndex_containsReturnsFalse",
                        idx -> assertThat(idx.contains(1)).isFalse()
                ),
                new CaseSpec(
                        "emptyIndex_sizeIsZero",
                        idx -> assertThat(idx.size()).isZero()
                ),
                new CaseSpec(
                        "emptyIndex_rangeReturnsEmptyList",
                        idx -> assertThat(rangeAsc(idx, 0, 100)).isEmpty()
                ),
                new CaseSpec(
                        "putInsertsFirstValue_putReturnsEmpty_getReturnsValue",
                        idx -> {
                            assertThat(idx.put(1, "a")).isEmpty();
                            assertThat(idx.get(1)).hasValue("a");
                        }
                ),
                new CaseSpec(
                        "putReplacesValue_putReturnsPrevious_getReturnsNewValue",
                        idx -> {
                            assertThat(idx.put(1, "a")).isEmpty();
                            assertThat(idx.put(1, "b")).hasValue("a");
                            assertThat(idx.get(1)).hasValue("b");
                        }
                ),
                new CaseSpec(
                        "removeExistingKey_returnsStoredValue_secondRemoveReturnsEmpty",
                        idx -> {
                            assertThat(idx.put(1, "a")).isEmpty();
                            assertThat(idx.remove(1)).hasValue("a");
                            assertThat(idx.remove(1)).isEmpty();
                            assertThat(idx.get(1)).isEmpty();
                        }
                ),
                new CaseSpec(
                        "replaceThenRemove_removeReturnsLastValue",
                        idx -> {
                            assertThat(idx.put(1, "a")).isEmpty();
                            assertThat(idx.put(1, "b")).hasValue("a");
                            assertThat(idx.remove(1)).hasValue("b");
                            assertThat(idx.remove(1)).isEmpty();
                        }
                ),
                new CaseSpec(
                        "getOnMissingKeyWhenNonEmpty_returnsEmpty_otherKeysUnchanged",
                        idx -> {
                            assertThat(idx.put(1, "a")).isEmpty();
                            assertThat(idx.put(2, "b")).isEmpty();
                            assertThat(idx.get(99)).isEmpty();
                            assertThat(idx.get(1)).hasValue("a");
                            assertThat(idx.get(2)).hasValue("b");
                        }
                ),
                new CaseSpec(
                        "removeOnMissingKeyWhenNonEmpty_returnsEmpty_otherKeysUnchanged",
                        idx -> {
                            assertThat(idx.put(1, "a")).isEmpty();
                            assertThat(idx.put(2, "b")).isEmpty();
                            assertThat(idx.remove(99)).isEmpty();
                            assertThat(idx.get(1)).hasValue("a");
                            assertThat(idx.get(2)).hasValue("b");
                        }
                ),
                new CaseSpec(
                        "twoDistinctKeys_eachPutReturnsEmpty",
                        idx -> {
                            assertThat(idx.put(10, "x")).isEmpty();
                            assertThat(idx.put(20, "y")).isEmpty();
                            assertThat(idx.get(10)).hasValue("x");
                            assertThat(idx.get(20)).hasValue("y");
                        }
                ),
                new CaseSpec(
                        "threeKeys_replaceMiddle_putReturnsPreviousValue",
                        idx -> {
                            assertThat(idx.put(1, "v1")).isEmpty();
                            assertThat(idx.put(2, "v2")).isEmpty();
                            assertThat(idx.put(3, "v3")).isEmpty();
                            assertThat(idx.put(2, "w2")).hasValue("v2");
                            assertThat(idx.get(2)).hasValue("w2");
                            assertThat(idx.get(1)).hasValue("v1");
                            assertThat(idx.get(3)).hasValue("v3");
                        }
                ),
                new CaseSpec(
                        "threeKeys_removeMiddle_returnsMiddleValue_othersRemain",
                        idx -> {
                            assertThat(idx.put(1, "a")).isEmpty();
                            assertThat(idx.put(2, "b")).isEmpty();
                            assertThat(idx.put(3, "c")).isEmpty();
                            assertThat(idx.remove(2)).hasValue("b");
                            assertThat(idx.get(2)).isEmpty();
                            assertThat(idx.get(1)).hasValue("a");
                            assertThat(idx.get(3)).hasValue("c");
                        }
                ),
                new CaseSpec(
                        "range_whenLowerBoundGreaterThanUpper_returnsEmpty",
                        idx -> {
                            assertThat(idx.put(1, "a")).isEmpty();
                            assertThat(idx.put(2, "b")).isEmpty();
                            assertThat(rangeAsc(idx, 5, 1)).isEmpty();
                        }
                ),
                new CaseSpec(
                        "range_singletonInclusive_containsOneEntry",
                        idx -> {
                            assertThat(idx.put(7, "x")).isEmpty();
                            assertThat(rangeAsc(idx, 7, 7)).containsExactly(Map.entry(7, "x"));
                        }
                ),
                new CaseSpec(
                        "range_twoKeys_inKeyOrder",
                        idx -> {
                            assertThat(idx.put(1, "a")).isEmpty();
                            assertThat(idx.put(3, "b")).isEmpty();
                            assertThat(rangeAsc(idx, 1, 3)).containsExactly(
                                    Map.entry(1, "a"),
                                    Map.entry(3, "b")
                            );
                        }
                ),
                new CaseSpec(
                        "removeThenPutSameKey_putReturnsEmpty",
                        idx -> {
                            assertThat(idx.put(5, "first")).isEmpty();
                            assertThat(idx.remove(5)).hasValue("first");
                            assertThat(idx.put(5, "second")).isEmpty();
                            assertThat(idx.get(5)).hasValue("second");
                        }
                ),
                new CaseSpec(
                        "threeKeys_removeInOrder_indexBecomesEmpty",
                        idx -> {
                            assertThat(idx.put(1, "v1")).isEmpty();
                            assertThat(idx.put(2, "v2")).isEmpty();
                            assertThat(idx.put(3, "v3")).isEmpty();
                            assertThat(idx.remove(1)).hasValue("v1");
                            assertThat(idx.remove(2)).hasValue("v2");
                            assertThat(idx.remove(3)).hasValue("v3");
                            assertThat(idx.size()).isZero();
                        }
                ),
                new CaseSpec(
                        "containsMatchesPresenceAfterPutsAndRemoves",
                        idx -> {
                            assertThat(idx.contains(1)).isFalse();
                            assertThat(idx.put(1, "a")).isEmpty();
                            assertThat(idx.contains(1)).isTrue();
                            assertThat(idx.contains(2)).isFalse();
                            assertThat(idx.remove(1)).hasValue("a");
                            assertThat(idx.contains(1)).isFalse();
                        }
                ),
                new CaseSpec(
                        "sizeTracksDistinctKeys",
                        idx -> {
                            assertThat(idx.size()).isZero();
                            assertThat(idx.put(1, "a")).isEmpty();
                            assertThat(idx.size()).isEqualTo(1);
                            assertThat(idx.put(1, "b")).hasValue("a");
                            assertThat(idx.size()).isEqualTo(1);
                            assertThat(idx.put(2, "c")).isEmpty();
                            assertThat(idx.size()).isEqualTo(2);
                            assertThat(idx.remove(1)).hasValue("b");
                            assertThat(idx.size()).isEqualTo(1);
                        }
                )
        );
    }

    final Stream<Arguments> factoriesAndCaseArguments() {
        return namedCases().flatMap(c ->
                indexFactories().map(factory -> arguments(factory, c))
        );
    }

    @ParameterizedTest(name = "{1}")
    @MethodSource("factoriesAndCaseArguments")
    void verifyBehavior(Supplier<OrderedVersionedIndex<Integer, String>> factory, CaseSpec spec) {
        spec.steps().accept(factory.get());
    }
}

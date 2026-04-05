package io.dsal.persistent.index.core;

import io.dsal.persistent.index.testsupport.TestKeyFixtures;
import io.dsal.persistent.index.testsupport.TreeTestAccess;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

class PersistentBPlusTreeTest {

    private static final int[] MAX_KEYS_VALUES = {2, 3, 4, 5};

    private static PersistentBPlusTree<Integer, String> newTree(int maxKeys) {
        return new PersistentBPlusTree<>(maxKeys, TestKeyFixtures.integerArrayKeyStorageFactory());
    }

    private record CaseSpec(String name, Consumer<PersistentBPlusTree<Integer, String>> steps) {
        @Override
        public String toString() {
            return name;
        }
    }

    private static Stream<CaseSpec> namedCases() {
        return Stream.of(
                new CaseSpec(
                        "emptyTree_getOnMissingKeyReturnsNull",
                        t -> assertThat(t.get(7)).isNull()
                ),
                new CaseSpec(
                        "emptyTree_removeOnMissingKeyReturnsNull",
                        t -> assertThat(t.remove(7)).isNull()
                ),
                new CaseSpec(
                        "emptyTree_rangeReturnsEmptyList",
                        t -> assertThat(t.range(0, 100)).isEmpty()
                ),
                new CaseSpec(
                        "putInsertsFirstValue_putReturnsNull_getReturnsValue",
                        t -> {
                            assertThat(t.put(1, "a")).isNull();
                            assertThat(t.get(1)).isEqualTo("a");
                        }
                ),
                new CaseSpec(
                        "putReplacesValue_putReturnsPrevious_getReturnsNewValue",
                        t -> {
                            assertThat(t.put(1, "a")).isNull();
                            assertThat(t.put(1, "b")).isEqualTo("a");
                            assertThat(t.get(1)).isEqualTo("b");
                        }
                ),
                new CaseSpec(
                        "removeExistingKey_returnsStoredValue_secondRemoveReturnsNull",
                        t -> {
                            assertThat(t.put(1, "a")).isNull();
                            assertThat(t.remove(1)).isEqualTo("a");
                            assertThat(t.remove(1)).isNull();
                            assertThat(t.get(1)).isNull();
                        }
                ),
                new CaseSpec(
                        "replaceThenRemove_removeReturnsLastValue_secondRemoveReturnsNull",
                        t -> {
                            assertThat(t.put(1, "a")).isNull();
                            assertThat(t.put(1, "b")).isEqualTo("a");
                            assertThat(t.remove(1)).isEqualTo("b");
                            assertThat(t.remove(1)).isNull();
                        }
                ),
                new CaseSpec(
                        "getOnMissingKeyWhenNonEmpty_returnsNull_otherKeysUnchanged",
                        t -> {
                            assertThat(t.put(1, "a")).isNull();
                            assertThat(t.put(2, "b")).isNull();
                            assertThat(t.get(99)).isNull();
                            assertThat(t.get(1)).isEqualTo("a");
                            assertThat(t.get(2)).isEqualTo("b");
                        }
                ),
                new CaseSpec(
                        "removeOnMissingKeyWhenNonEmpty_returnsNull_otherKeysUnchanged",
                        t -> {
                            assertThat(t.put(1, "a")).isNull();
                            assertThat(t.put(2, "b")).isNull();
                            assertThat(t.remove(99)).isNull();
                            assertThat(t.get(1)).isEqualTo("a");
                            assertThat(t.get(2)).isEqualTo("b");
                        }
                ),
                new CaseSpec(
                        "twoDistinctKeys_eachPutReturnsNull",
                        t -> {
                            assertThat(t.put(10, "x")).isNull();
                            assertThat(t.put(20, "y")).isNull();
                            assertThat(t.get(10)).isEqualTo("x");
                            assertThat(t.get(20)).isEqualTo("y");
                        }
                ),
                new CaseSpec(
                        "removeOneOfTwoKeys_returnsThatValue_otherKeyRemains_untilSecondRemoveEmptiesTree",
                        t -> {
                            assertThat(t.put(1, "a")).isNull();
                            assertThat(t.put(2, "b")).isNull();
                            assertThat(t.remove(1)).isEqualTo("a");
                            assertThat(t.get(1)).isNull();
                            assertThat(t.get(2)).isEqualTo("b");
                            assertThat(t.remove(2)).isEqualTo("b");
                            assertThat(TreeTestAccess.root(t)).isNull();
                        }
                ),
                new CaseSpec(
                        "threeKeys_replaceMiddle_putReturnsPreviousValue",
                        t -> {
                            assertThat(t.put(1, "v1")).isNull();
                            assertThat(t.put(2, "v2")).isNull();
                            assertThat(t.put(3, "v3")).isNull();
                            assertThat(t.put(2, "w2")).isEqualTo("v2");
                            assertThat(t.get(2)).isEqualTo("w2");
                            assertThat(t.get(1)).isEqualTo("v1");
                            assertThat(t.get(3)).isEqualTo("v3");
                        }
                ),
                new CaseSpec(
                        "threeKeys_removeMiddle_returnsMiddleValue_othersRemain",
                        t -> {
                            assertThat(t.put(1, "a")).isNull();
                            assertThat(t.put(2, "b")).isNull();
                            assertThat(t.put(3, "c")).isNull();
                            assertThat(t.remove(2)).isEqualTo("b");
                            assertThat(t.get(2)).isNull();
                            assertThat(t.get(1)).isEqualTo("a");
                            assertThat(t.get(3)).isEqualTo("c");
                        }
                ),
                new CaseSpec(
                        "range_whenLowerBoundGreaterThanUpper_returnsEmpty",
                        t -> {
                            assertThat(t.put(1, "a")).isNull();
                            assertThat(t.put(2, "b")).isNull();
                            assertThat(t.range(5, 1)).isEmpty();
                        }
                ),
                new CaseSpec(
                        "range_singletonInclusive_containsOneEntry",
                        t -> {
                            assertThat(t.put(7, "x")).isNull();
                            assertThat(t.range(7, 7)).containsExactly(KeyVal.of(7, "x"));
                        }
                ),
                new CaseSpec(
                        "range_twoKeys_inKeyOrder",
                        t -> {
                            assertThat(t.put(1, "a")).isNull();
                            assertThat(t.put(3, "b")).isNull();
                            assertThat(t.range(1, 3)).containsExactly(
                                    KeyVal.of(1, "a"),
                                    KeyVal.of(3, "b")
                            );
                        }
                ),
                new CaseSpec(
                        "removeThenPutSameKey_putReturnsNull",
                        t -> {
                            assertThat(t.put(5, "first")).isNull();
                            assertThat(t.remove(5)).isEqualTo("first");
                            assertThat(t.put(5, "second")).isNull();
                            assertThat(t.get(5)).isEqualTo("second");
                        }
                ),
                new CaseSpec(
                        "threeKeys_insertOrder_thenRemoveInKeyOrder_treeBecomesEmpty",
                        t -> {
                            assertThat(t.put(1, "v1")).isNull();
                            assertThat(t.put(2, "v2")).isNull();
                            assertThat(t.put(3, "v3")).isNull();
                            assertThat(t.remove(1)).isEqualTo("v1");
                            assertThat(t.remove(2)).isEqualTo("v2");
                            assertThat(t.remove(3)).isEqualTo("v3");
                            assertThat(TreeTestAccess.root(t)).isNull();
                        }
                )
        );
    }

    static Stream<Arguments> maxKeysAndCaseArguments() {
        return Arrays.stream(MAX_KEYS_VALUES).boxed().flatMap(
                maxKeys -> namedCases().map(c -> arguments(maxKeys, c))
        );
    }

    @ParameterizedTest(name = "maxKeys={0}, case={1}")
    @MethodSource("maxKeysAndCaseArguments")
    void verifyPutGetRemoveReturnValuesAndRange(int maxKeys, CaseSpec spec) {
        spec.steps().accept(newTree(maxKeys));
    }
}

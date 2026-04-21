package io.dsal.versioned.index.util;

import io.dsal.versioned.index.layout.ArrayKeyStorage;
import org.junit.jupiter.api.Test;

import java.util.Comparator;

import static org.assertj.core.api.Assertions.assertThat;

class SearchTest {

    private static final Comparator<Integer> CMP = Comparator.naturalOrder();

    @Test
    void lowerBoundEmptyComparator() {
        var empty = new ArrayKeyStorage<>(new Integer[0], CMP);
        var lb = Search.lowerBound(empty, 5);
        assertThat(lb.found()).isFalse();
        assertThat(lb.idx()).isZero();
    }

    @Test
    void lowerBoundSingleLessGreaterEqual() {
        var one = new ArrayKeyStorage<>(new Integer[]{10}, CMP);
        assertThat(Search.lowerBound(one, 5)).satisfies(lb -> {
            assertThat(lb.found()).isFalse();
            assertThat(lb.idx()).isZero();
        });
        assertThat(Search.lowerBound(one, 10)).satisfies(lb -> {
            assertThat(lb.found()).isTrue();
            assertThat(lb.idx()).isZero();
        });
        assertThat(Search.lowerBound(one, 20)).satisfies(lb -> {
            assertThat(lb.found()).isFalse();
            assertThat(lb.idx()).isEqualTo(1);
        });
    }

    @Test
    void lowerBoundMultipleHitsAndGaps() {
        var keys = new ArrayKeyStorage<>(new Integer[]{1, 3, 5, 7, 9}, CMP);
        assertThat(Search.lowerBound(keys, 1).found()).isTrue();
        assertThat(Search.lowerBound(keys, 1).idx()).isZero();
        assertThat(Search.lowerBound(keys, 4).found()).isFalse();
        assertThat(Search.lowerBound(keys, 4).idx()).isEqualTo(2);
        assertThat(Search.lowerBound(keys, 5).found()).isTrue();
        assertThat(Search.lowerBound(keys, 5).idx()).isEqualTo(2);
        assertThat(Search.lowerBound(keys, 10).found()).isFalse();
        assertThat(Search.lowerBound(keys, 10).idx()).isEqualTo(5);
    }
}

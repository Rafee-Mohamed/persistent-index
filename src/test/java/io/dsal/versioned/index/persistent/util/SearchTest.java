package io.dsal.versioned.index.persistent.util;

import io.dsal.versioned.index.persistent.layout.ArrayKeyStorage;
import io.dsal.versioned.index.persistent.util.Search;
import org.junit.jupiter.api.Test;

import java.util.Comparator;

import static org.assertj.core.api.Assertions.assertThat;

class SearchTest {

    private static final Comparator<Integer> CMP = Comparator.naturalOrder();

    private static ArrayKeyStorage<Integer> storage(Integer... keys) {
        return new ArrayKeyStorage<>(keys, CMP);
    }

    // -------------------------------------------------------------------------
    // find — exact match or -1
    // -------------------------------------------------------------------------

    @Test
    void findEmptyReturnsMinusOne() {
        var ks = storage();
        assertThat(Search.find(ks, 5)).isEqualTo(-1);
    }

    @Test
    void findHitAndMiss() {
        var ks = storage(1, 3, 5, 7, 9);
        assertThat(Search.find(ks, 1)).isZero();
        assertThat(Search.find(ks, 5)).isEqualTo(2);
        assertThat(Search.find(ks, 9)).isEqualTo(4);
        assertThat(Search.find(ks, 4)).isEqualTo(-1);
        assertThat(Search.find(ks, 0)).isEqualTo(-1);
        assertThat(Search.find(ks, 10)).isEqualTo(-1);
    }

    // -------------------------------------------------------------------------
    // lowerBound — first index where stored[i] >= key
    // -------------------------------------------------------------------------

    @Test
    void lowerBoundEmptyReturnsZero() {
        var ks = storage();
        assertThat(Search.lowerBound(ks, 5)).isZero();
    }

    @Test
    void lowerBoundSingleElement() {
        var ks = storage(10);
        assertThat(Search.lowerBound(ks, 5)).isZero();    // 10 >= 5, so first hit is idx 0
        assertThat(Search.lowerBound(ks, 10)).isZero();   // exact match
        assertThat(Search.lowerBound(ks, 20)).isEqualTo(1); // beyond all keys
    }

    @Test
    void lowerBoundMultipleHitsAndGaps() {
        var ks = storage(1, 3, 5, 7, 9);
        assertThat(Search.lowerBound(ks, 1)).isZero();
        assertThat(Search.lowerBound(ks, 4)).isEqualTo(2); // first i where [i] >= 4 is index 2 (key 5)
        assertThat(Search.lowerBound(ks, 5)).isEqualTo(2);
        assertThat(Search.lowerBound(ks, 0)).isZero();
        assertThat(Search.lowerBound(ks, 10)).isEqualTo(5); // beyond all
    }

    // -------------------------------------------------------------------------
    // upperBound — first index where stored[i] > key
    // -------------------------------------------------------------------------

    @Test
    void upperBoundEmptyReturnsZero() {
        var ks = storage();
        assertThat(Search.upperBound(ks, 5)).isZero();
    }

    @Test
    void upperBoundExactMatchPointsPastMatch() {
        var ks = storage(1, 3, 5, 7, 9);
        assertThat(Search.upperBound(ks, 5)).isEqualTo(3); // first i where [i] > 5 is index 3 (key 7)
        assertThat(Search.upperBound(ks, 4)).isEqualTo(2); // first i where [i] > 4 is index 2 (key 5)
        assertThat(Search.upperBound(ks, 9)).isEqualTo(5); // beyond all
        assertThat(Search.upperBound(ks, 0)).isZero();
        assertThat(Search.upperBound(ks, 10)).isEqualTo(5);
    }

    // -------------------------------------------------------------------------
    // floor — last index where stored[i] <= key, or -1
    // -------------------------------------------------------------------------

    @Test
    void floorBelowAllKeysReturnsMinusOne() {
        var ks = storage(1, 3, 5, 7, 9);
        assertThat(Search.floor(ks, 0)).isEqualTo(-1);
    }

    @Test
    void floorExactAndBetween() {
        var ks = storage(1, 3, 5, 7, 9);
        assertThat(Search.floor(ks, 5)).isEqualTo(2); // 5 itself at index 2
        assertThat(Search.floor(ks, 4)).isEqualTo(1); // 3 at index 1 is the floor of 4
        assertThat(Search.floor(ks, 9)).isEqualTo(4);
        assertThat(Search.floor(ks, 10)).isEqualTo(4);
    }

    // -------------------------------------------------------------------------
    // predecessor — last index where stored[i] < key, or -1
    // -------------------------------------------------------------------------

    @Test
    void predecessorAtOrBelowMinReturnsMinusOne() {
        var ks = storage(1, 3, 5, 7, 9);
        assertThat(Search.predecessor(ks, 0)).isEqualTo(-1);
        assertThat(Search.predecessor(ks, 1)).isEqualTo(-1); // nothing strictly < 1
    }

    @Test
    void predecessorExactAndBetween() {
        var ks = storage(1, 3, 5, 7, 9);
        assertThat(Search.predecessor(ks, 5)).isEqualTo(1); // 3 at index 1 < 5
        assertThat(Search.predecessor(ks, 4)).isEqualTo(1); // 3 at index 1 < 4
        assertThat(Search.predecessor(ks, 9)).isEqualTo(3); // 7 at index 3 < 9
        assertThat(Search.predecessor(ks, 10)).isEqualTo(4);
    }

    // -------------------------------------------------------------------------
    // findAndLowerBound — combined exact-match + insertion point
    // -------------------------------------------------------------------------

    @Test
    void findAndLowerBoundEmpty() {
        var ks = storage();
        var lb = Search.findAndLowerBound(ks, 5);
        assertThat(lb.found()).isFalse();
        assertThat(lb.idx()).isZero();
    }

    @Test
    void findAndLowerBoundHitAndMiss() {
        var ks = storage(1, 3, 5, 7, 9);
        var hit = Search.findAndLowerBound(ks, 5);
        assertThat(hit.found()).isTrue();
        assertThat(hit.idx()).isEqualTo(2);

        var miss = Search.findAndLowerBound(ks, 4);
        assertThat(miss.found()).isFalse();
        assertThat(miss.idx()).isEqualTo(2); // insertion point for 4

        var beyond = Search.findAndLowerBound(ks, 10);
        assertThat(beyond.found()).isFalse();
        assertThat(beyond.idx()).isEqualTo(5);

        var before = Search.findAndLowerBound(ks, 0);
        assertThat(before.found()).isFalse();
        assertThat(before.idx()).isZero();
    }
}

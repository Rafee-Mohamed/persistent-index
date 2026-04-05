package io.dsal.persistent.index.layout;

import io.dsal.persistent.index.testsupport.KeyStorageTestSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ValueStorageTest {

    @Test
    void ofSizeValOob() {
        var v = ValueStorage.of("a");
        assertThat(v.size()).isEqualTo(1);
        assertThat(v.val(0)).isEqualTo("a");
        assertThatThrownBy(() -> v.val(1)).isInstanceOf(IndexOutOfBoundsException.class);
    }

    @Test
    void insertPositions() {
        var a = ValueStorage.of("a");
        var b = a.insert(0, "b");
        assertThat(b.size()).isEqualTo(2);
        assertThat(b.val(0)).isEqualTo("b");
        assertThat(b.val(1)).isEqualTo("a");
        assertThat(a.val(0)).isEqualTo("a");

        var base = ValueStorage.of("x").insert(1, "y").insert(2, "z");
        assertThat(base.size()).isEqualTo(3);
        var mid = base.insert(1, "m");
        assertThat(mid.val(1)).isEqualTo("m");
    }

    @Test
    void removeFirstMiddleLast() {
        var v = ValueStorage.of("a").insert(1, "b").insert(2, "c");
        assertThat(asList(v.remove(0))).containsExactly("b", "c");
        assertThat(asList(v.remove(1))).containsExactly("a", "c");
        assertThat(asList(v.remove(2))).containsExactly("a", "b");
    }

    @Test
    void replace() {
        var v = ValueStorage.of("a").insert(1, "b");
        var w = v.replace(0, "z");
        assertThat(w.val(0)).isEqualTo("z");
        assertThat(w.val(1)).isEqualTo("b");
        assertThat(v.val(0)).isEqualTo("a");
    }

    @Test
    void removeAndInsertMatchesRemoveThenInsert() {
        var v = ValueStorage.of("a").insert(1, "b").insert(2, "c").insert(3, "d");
        var expected = v.remove(1).insert(2, "X");
        assertThat(asList(v.removeAndInsert(1, 2, "X"))).isEqualTo(asList(expected));
    }

    @Test
    void merge() {
        var a = ValueStorage.of("a").insert(1, "b");
        var b = ValueStorage.of("c").insert(1, "d");
        var m = a.merge(b);
        assertThat(asList(m)).containsExactly("a", "b", "c", "d");
        assertThat(a.size()).isEqualTo(2);
    }

    @Test
    void insertAndMerge() {
        var a = ValueStorage.of("a").insert(1, "b");
        var tail = ValueStorage.of("y").insert(1, "z");
        var m = a.insertAndMerge(1, "m", tail);
        assertThat(asList(m)).containsExactly("a", "m", "b", "y", "z");
    }

    @Test
    void insertAndSplitMatchesParallelKeys() {
        var ks = KeyStorageTestSupport.arraySorted(1, 3, 5, 7);
        var vs = ValueStorage.of("a").insert(1, "b").insert(2, "c").insert(3, "d");

        int insertIdx = 2;
        int splitIdx = 3;
        var keySplit = ks.insertAndSplit(insertIdx, splitIdx, 4);
        var valSplit = vs.insertAndSplit(insertIdx, splitIdx, "x");

        assertThat(valSplit.left().size()).isEqualTo(keySplit.left().size());
        assertThat(valSplit.right().size()).isEqualTo(keySplit.right().size());
    }

    private static <V> java.util.List<V> asList(ValueStorage<V> vs) {
        var out = new java.util.ArrayList<V>();
        for (int i = 0; i < vs.size(); i++) {
            out.add(vs.val(i));
        }
        return out;
    }
}


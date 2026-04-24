package io.dsal.versioned.index.persistent.core;

import io.dsal.versioned.index.persistent.layout.ValueStorage;
import io.dsal.versioned.index.persistent.testsupport.IndexTestSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChildrenTest {

    private static Node.Leaf<Integer, String> leaf(int key, String tag) {
        return new Node.Leaf<>(IndexTestSupport.integerKeyStorageFactory().single(key), ValueStorage.of(tag));
    }

    private static String tag(Node<Integer, String> n) {
        if (n instanceof Node.Leaf<Integer, String> lf) {
            return lf.values().val(0);
        }
        throw new AssertionError("expected leaf");
    }

    /**
     * Four child slots whose tags appear in order {@code a}, {@code c}, {@code d}, {@code b}
     * (not left-to-right key order), built as {@code of(a,b).insert(1,c).insert(2,d)}.
     */
    private static Children<Integer, String> fourSampleChildren() {
        var a = leaf(0, "a");
        var b = leaf(1, "b");
        var c = leaf(2, "c");
        var d = leaf(3, "d");
        return Children.of(a, b).insert(1, c).insert(2, d);
    }

    @Test
    void ofExposesSizeChildAccessorsAndThrowsOnOutOfBoundsIndex() {
        var a = leaf(0, "a");
        var b = leaf(1, "b");
        var ch = Children.of(a, b);
        assertThat(ch.size()).isEqualTo(2);
        assertThat(ch.child(0)).isSameAs(a);
        assertThat(ch.child(1)).isSameAs(b);
        assertThatThrownBy(() -> ch.child(2)).isInstanceOf(IndexOutOfBoundsException.class);
    }

    @Test
    void replace() {
        var a = leaf(0, "a");
        var b = leaf(1, "b");
        var n = leaf(2, "n");
        var ch = Children.of(a, b).replace(0, n);
        assertThat(ch.size()).isEqualTo(2);
        assertThat(tag(ch.child(0))).isEqualTo("n");
        assertThat(ch.child(1)).isSameAs(b);
    }

    @Test
    void insertSingle() {
        var a = leaf(0, "a");
        var b = leaf(1, "b");
        var c = leaf(2, "c");
        var ch = Children.of(a, b).insert(1, c);
        assertThat(ch.size()).isEqualTo(3);
        assertThat(tag(ch.child(0))).isEqualTo("a");
        assertThat(tag(ch.child(1))).isEqualTo("c");
        assertThat(tag(ch.child(2))).isEqualTo("b");
    }

    @Test
    void insertSplitPair() {
        var c0 = leaf(0, "c0");
        var c1 = leaf(1, "c1");
        var c2 = leaf(2, "c2");
        var l = leaf(10, "L");
        var r = leaf(11, "R");
        var ch = Children.of(c0, c2).insert(1, c1).insert(1, l, r);
        assertThat(ch.size()).isEqualTo(4);
        assertThat(tag(ch.child(0))).isEqualTo("c0");
        assertThat(tag(ch.child(1))).isEqualTo("L");
        assertThat(tag(ch.child(2))).isEqualTo("R");
        assertThat(tag(ch.child(3))).isEqualTo("c2");
    }

    @Test
    void replaceAdjacentPair() {
        var c0 = leaf(0, "c0");
        var a = leaf(1, "a");
        var b = leaf(2, "b");
        var c3 = leaf(3, "c3");
        var l = leaf(4, "L");
        var r = leaf(5, "R");
        var base = Children.of(c0, c3).insert(1, a).insert(2, b);
        var ch = base.replace(1, l, r);
        assertThat(ch.size()).isEqualTo(4);
        assertThat(tag(ch.child(1))).isEqualTo("L");
        assertThat(tag(ch.child(2))).isEqualTo("R");
    }

    @Test
    void removeFirstMiddleLast() {
        var ch = fourSampleChildren();
        assertThat(tag(ch.remove(0).child(0))).isEqualTo("c");
        assertThat(ch.remove(1).size()).isEqualTo(3);
        assertThat(tag(ch.remove(3).child(2))).isEqualTo("d");
    }

    @Test
    void removeAndReplace() {
        var c0 = leaf(0, "c0");
        var c1 = leaf(1, "c1");
        var c2 = leaf(2, "c2");
        var c3 = leaf(3, "c3");
        var m = leaf(9, "M");
        var base = Children.of(c0, c3).insert(1, c1).insert(2, c2);
        var ch = base.removeAndReplace(1, m);
        assertThat(ch.size()).isEqualTo(3);
        assertThat(tag(ch.child(0))).isEqualTo("c0");
        assertThat(tag(ch.child(1))).isEqualTo("M");
        assertThat(tag(ch.child(2))).isEqualTo("c3");
    }

    @Test
    void merge() {
        var ch1 = Children.of(leaf(0, "a"), leaf(1, "b"));
        var ch2 = Children.of(leaf(2, "c"), leaf(3, "d"));
        var m = ch1.merge(ch2);
        assertThat(m.size()).isEqualTo(4);
        assertThat(tag(m.child(3))).isEqualTo("d");
    }

    @Test
    void removeAndInsertMatchesRemoveThenInsert() {
        var ch = fourSampleChildren();
        var x = leaf(9, "X");
        var expected = ch.remove(1).insert(2, x);
        assertThat(sameTags(ch.removeAndInsert(1, 2, x), expected)).isTrue();
    }

    @Test
    void removeAndInsertInsertBeforeRemoveBranch() {
        var n0 = leaf(0, "0");
        var n1 = leaf(1, "1");
        var n2 = leaf(2, "2");
        var x = leaf(9, "X");
        var base = Children.of(n0, n2).insert(1, n1);
        var expected = base.remove(2).insert(0, x);
        assertThat(sameTags(base.removeAndInsert(2, 0, x), expected)).isTrue();
    }

    @Test
    void insertAndSplitFirstBranch() {
        var c0 = leaf(0, "c0");
        var c1 = leaf(1, "c1");
        var c2 = leaf(2, "c2");
        var c3 = leaf(3, "c3");
        var l = leaf(10, "L");
        var r = leaf(11, "R");
        var base = Children.of(c0, c3).insert(1, c1).insert(2, c2);
        int insertIdx = 1;
        int splitIdx = 2;
        var split = base.insertAndSplit(insertIdx, splitIdx, l, r);
        assertThat(split.left().size() + split.right().size()).isEqualTo(base.size() + 1);
        assertThat(containsTag(split.left(), "L")).isTrue();
        assertThat(containsTag(split.left(), "R") || containsTag(split.right(), "R")).isTrue();
    }

    @Test
    void insertAndSplitSecondBranch() {
        var c0 = leaf(0, "c0");
        var c1 = leaf(1, "c1");
        var c2 = leaf(2, "c2");
        var l = leaf(10, "L");
        var r = leaf(11, "R");
        var base = Children.of(c0, c2).insert(1, c1);
        var split = base.insertAndSplit(0, 2, l, r);
        assertThat(split.left().size()).isPositive();
        assertThat(split.right().size()).isPositive();
        assertThat(split.left().size() + split.right().size()).isEqualTo(base.size() + 1);
    }

    private static boolean sameTags(Children<Integer, String> a, Children<Integer, String> b) {
        if (a.size() != b.size()) {
            return false;
        }
        for (int i = 0; i < a.size(); i++) {
            if (!tag(a.child(i)).equals(tag(b.child(i)))) {
                return false;
            }
        }
        return true;
    }

    private static boolean containsTag(Children<Integer, String> ch, String t) {
        for (int i = 0; i < ch.size(); i++) {
            if (tag(ch.child(i)).equals(t)) {
                return true;
            }
        }
        return false;
    }
}

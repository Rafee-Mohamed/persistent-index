package io.dsal.versioned.index.api.property;

import io.dsal.versioned.index.api.Direction;
import io.dsal.versioned.index.api.OrderedVersionedIndex;
import io.dsal.versioned.index.api.TxnAction;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Abstract property-based invariant suite for any {@link OrderedVersionedIndex} implementation.
 * Concrete subclasses supply the index factory, key ordering, and key arbitraries.
 * Tree-structural invariants (e.g. B+ tree node fill ratios) are left to subclass {@code @Property} methods.
 *
 * @param <K> key type under test
 */
public abstract class AbstractOrderedIndexInvariantTest<K> {

    protected abstract Arbitrary<OrderedVersionedIndex<K, String>> indexArbitrary();

    protected abstract Comparator<K> keyOrder();

    protected abstract Arbitrary<List<K>> createKeysArbitrary();

    @Provide("anIndex")
    final Arbitrary<OrderedVersionedIndex<K, String>> anIndex() {
        return indexArbitrary();
    }

    @Provide
    final Arbitrary<List<K>> keys() {
        return createKeysArbitrary();
    }

    /** Deduplicates keys by the key order comparator — safe for {@code byte[]} and any other type. */
    private List<K> unique(List<K> keys) {
        var set = new TreeSet<>(keyOrder());
        set.addAll(keys);
        return new ArrayList<>(set);
    }

    @Property
    void putAndGetIsIdempotent(
            @ForAll("anIndex") OrderedVersionedIndex<K, String> index,
            @ForAll("keys") List<K> keys) {
        for (var k : keys) {
            index.put(k, "first");
            index.put(k, "second");
            assertThat(index.get(k)).hasValue("second");
        }
    }

    @Property
    void containsMatchesGet(
            @ForAll("anIndex") OrderedVersionedIndex<K, String> index,
            @ForAll("keys") List<K> keys) {
        for (var k : keys) index.put(k, "v");
        for (var k : keys) {
            assertThat(index.contains(k)).isEqualTo(index.get(k).isPresent());
        }
    }

    @Property
    void removeReturnsAbsentAfterRemoval(
            @ForAll("anIndex") OrderedVersionedIndex<K, String> index,
            @ForAll("keys") List<K> keys) {
        var unique = unique(keys);
        for (var k : unique) index.put(k, "v");
        for (var k : unique) {
            assertThat(index.remove(k)).isPresent();
            assertThat(index.remove(k)).isEmpty();
            assertThat(index.contains(k)).isFalse();
        }
    }

    @Property
    void iterationAscIsStrictlyOrdered(
            @ForAll("anIndex") OrderedVersionedIndex<K, String> index,
            @ForAll("keys") List<K> keys) {
        for (var k : keys) index.put(k, "v");

        var entries = new ArrayList<K>();
        index.forEach(Direction.ASC, (k, v) -> entries.add(k));

        var cmp = keyOrder();
        for (int i = 0; i < entries.size() - 1; i++) {
            assertThat(cmp.compare(entries.get(i), entries.get(i + 1))).isLessThan(0);
        }
    }

    @Property
    void iterationDescIsReverseOfAsc(
            @ForAll("anIndex") OrderedVersionedIndex<K, String> index,
            @ForAll("keys") List<K> keys) {
        for (var k : keys) index.put(k, "v");

        var asc = new ArrayList<K>();
        index.forEach(Direction.ASC, (k, v) -> asc.add(k));

        var desc = new ArrayList<K>();
        index.forEach(Direction.DESC, (k, v) -> desc.add(k));

        assertThat(desc).hasSize(asc.size());
        for (int i = 0; i < asc.size(); i++) {
            assertThat(keyOrder().compare(asc.get(i), desc.get(desc.size() - 1 - i))).isZero();
        }
    }

    @Property
    void sizeMatchesDistinctKeyCount(
            @ForAll("anIndex") OrderedVersionedIndex<K, String> index,
            @ForAll("keys") List<K> keys) {
        var unique = unique(keys);
        for (var k : unique) index.put(k, "v");
        assertThat(index.size()).isEqualTo(unique.size());
    }

    @Property
    void snapshotIsolationFromSubsequentPuts(
            @ForAll("anIndex") OrderedVersionedIndex<K, String> index,
            @ForAll("keys") List<K> keys) {
        if (keys.isEmpty()) return;
        K k0 = keys.get(0);
        index.put(k0, "before");

        var snap = index.snapshot();
        assertThat(snap.get(k0)).hasValue("before");

        index.put(k0, "after");

        assertThat(snap.get(k0)).hasValue("before");
        assertThat(index.get(k0)).hasValue("after");
    }

    @Property
    void snapshotIsolationFromSubsequentRemoves(
            @ForAll("anIndex") OrderedVersionedIndex<K, String> index,
            @ForAll("keys") List<K> keys) {
        if (keys.isEmpty()) return;
        K k0 = keys.get(0);
        index.put(k0, "v");

        var snap = index.snapshot();
        index.remove(k0);

        assertThat(snap.get(k0)).hasValue("v");
        assertThat(index.get(k0)).isEmpty();
    }

    @Property
    void txnReadYourWrites(
            @ForAll("anIndex") OrderedVersionedIndex<K, String> index,
            @ForAll("keys") List<K> keys) {
        if (keys.isEmpty()) return;
        K k0 = keys.get(0);

        index.txn(th -> {
            th.put(k0, "txn-val");
            assertThat(th.get(k0)).hasValue("txn-val");
        });

        assertThat(index.get(k0)).hasValue("txn-val");
    }

    @Property
    void txnSnapshotDoesNotIncludeTxnLocalMutations(
            @ForAll("anIndex") OrderedVersionedIndex<K, String> index,
            @ForAll("keys") List<K> keys) {
        if (keys.isEmpty()) return;
        K k0 = keys.get(0);

        index.txn( th -> {
            th.put(k0, "txn-local");
            var committedView = th.snapshot();
            assertThat(committedView.get(k0)).isEmpty();
        });
    }
}

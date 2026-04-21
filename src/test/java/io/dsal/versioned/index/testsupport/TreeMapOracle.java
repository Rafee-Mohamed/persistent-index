package io.dsal.versioned.index.testsupport;

import io.dsal.versioned.index.core.KeyVal;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.TreeMap;

/**
 * Reference model for {@link io.dsal.versioned.index.core.PersistentBPlusTree} tests:
 * a {@link TreeMap} with the same comparator and inclusive-range semantics as the tree
 * ({@code from &gt; to} in key order yields an empty range, matching
 * {@link io.dsal.versioned.index.core.PersistentBPlusTree#range(Object, Object)}).
 *
 * @param <K> key type
 * @param <V> value type
 */
public final class TreeMapOracle<K, V> {

    private final TreeMap<K, V> map;
    private final Comparator<? super K> cmp;

    public TreeMapOracle(Comparator<? super K> keyOrder) {
        this.cmp = keyOrder;
        this.map = new TreeMap<>(keyOrder);
    }

    public V put(K key, V val) {
        return map.put(key, val);
    }

    public V remove(K key) {
        return map.remove(key);
    }

    public V get(K key) {
        return map.get(key);
    }

    /**
     * Inclusive {@code [from, to]} in ascending order; empty when {@code from} is
     * strictly greater than {@code to} in key order, or when the map is empty.
     */
    public List<KeyVal<K, V>> rangeInclusive(K from, K to) {
        if (map.isEmpty()) {
            return List.of();
        }
        if (cmp.compare(from, to) > 0) {
            return List.of();
        }
        var out = new ArrayList<KeyVal<K, V>>();
        for (var e : map.subMap(from, true, to, true).entrySet()) {
            out.add(new KeyVal<>(e.getKey(), e.getValue()));
        }
        return out;
    }

    /** All entries in ascending key order (matches full tree iteration). */
    public List<KeyVal<K, V>> allEntriesInOrder() {
        var out = new ArrayList<KeyVal<K, V>>(map.size());
        for (var e : map.entrySet()) {
            out.add(new KeyVal<>(e.getKey(), e.getValue()));
        }
        return out;
    }

    public int size() {
        return map.size();
    }

    public boolean isEmpty() {
        return map.isEmpty();
    }
}
